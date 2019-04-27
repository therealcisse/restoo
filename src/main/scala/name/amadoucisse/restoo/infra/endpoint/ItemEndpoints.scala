package name.amadoucisse.restoo
package infra
package endpoint

import scala.language.higherKinds
import cats.effect.{ Clock, Sync }
import cats.implicits._
import cats.Apply
import io.circe.generic.auto._
import io.circe.syntax._
import org.http4s.dsl.Http4sDsl
import org.http4s.{ HttpRoutes, QueryParamDecoder }
import domain._
import domain.items._
import domain.entries._
import config.{ AppConf, SwaggerConf }
import http.{ HttpErrorHandler, JsonPatch, OrderBy, Page, SortBy, SwaggerSpec }
import service.{ Id, Items, Stocks }
import service.Id._
import service.Items._
import service.Stocks._
import utils.Validation
import eu.timepit.refined._
import eu.timepit.refined.auto._

import java.util.concurrent.TimeUnit
import java.time.Instant

import cats.mtl.ApplicativeAsk

final class ItemEndpoints[
    F[_]: Sync: Clock
](implicit idA: ApplicativeAsk[F, Id[F]], itemsA: ApplicativeAsk[F, Items[F]], stocksA: ApplicativeAsk[F, Stocks[F]])
    extends Http4sDsl[F]
    with Codecs {
  import ItemEndpoints._

  private def createEndpoint(): HttpRoutes[F] =
    HttpRoutes.of[F] {
      case req @ POST → Root ⇒
        for {
          itemRequest ← req.as[ItemRequest]

          (name, price, category) ← itemRequest.validate

          newId ← newItemId[F]

          now ← Clock[F].monotonic(TimeUnit.MILLISECONDS)

          item = Item(
            name = name,
            price = price,
            category = category,
            createdAt = DateTime(Instant.ofEpochMilli(now)),
            updatedAt = DateTime(Instant.ofEpochMilli(now)),
            id = newId,
          )

          _ ← createItem[F](item)

          resp ← Created(item.asJson)
        } yield resp

    }

  private def updateEndpoint(): HttpRoutes[F] =
    HttpRoutes.of[F] {
      case req @ PUT → (Root / ItemId(id)) ⇒
        for {
          item ← getItem[F](id)

          itemRequest ← req.as[ItemRequest]

          (name, price, category) ← itemRequest.validate

          now ← Clock[F].monotonic(TimeUnit.MILLISECONDS)

          updated = item.copy(
            name = name,
            price = price,
            category = category,
            updatedAt = DateTime(Instant.ofEpochMilli(now))
          )

          _ ← updateItem[F](updated)

          resp ← Ok(updated.asJson)
        } yield resp

    }

  private def patchEndpoint(): HttpRoutes[F] = {

    @annotation.tailrec
    def isValidJsonPatchForItem(patches: Vector[JsonPatch]): Boolean =
      patches match {
        case xs :+ s ⇒
          refineV[Item.PatchableField](s.path) match {
            case Right(_) ⇒ isValidJsonPatchForItem(xs)
            case Left(_)  ⇒ false
          }

        case _ ⇒ true
      }

    HttpRoutes.of[F] {
      case req @ PATCH → (Root / ItemId(id)) ⇒
        for {

          patches ← req
            .as[Vector[JsonPatch]]
            .ensureOr(patches ⇒ AppError.invalidJsonPatch(s"Could not create request from json patch : $patches")) {
              patches ⇒
                patches.nonEmpty && isValidJsonPatchForItem(patches)
            }

          item ← getItem[F](id)

          itemRequest ← {
            val unitR = ItemRequest.fromItem(item)

            val result = patches.foldLeft(unitR.asJson) { (input, patch) ⇒
              patch.applyPatch(input)
            }

            val maybeItemRequest = result.as[ItemRequest]

            maybeItemRequest.fold(
              r ⇒
                Sync[F].raiseError(
                  AppError
                    .invalidJsonPatch(s"Error : ${r.message}. Could not create request from json patch : $patches")
              ),
              Sync[F].pure
            )
          }

          now ← Clock[F].monotonic(TimeUnit.MILLISECONDS)

          (name, price, category) ← itemRequest.validate

          updated = item.copy(
            name = name,
            price = price,
            category = category,
            updatedAt = DateTime(Instant.ofEpochMilli(now))
          )
          _ ← updateItem[F](updated)

          resp ← Ok(updated.asJson)
        } yield resp

    }
  }

  private def listEndpoint(): HttpRoutes[F] = {
    implicit val categoryQueryParamDecoder: QueryParamDecoder[Category] =
      QueryParamDecoder[String].map(Category(_))

    implicit val offsetQueryParamDecoder: QueryParamDecoder[Instant] =
      QueryParamDecoder[Long].map(Instant.ofEpochMilli)

    object OptionalMarkerQueryParamMatcher extends OptionalQueryParamDecoderMatcher[Instant]("marker")

    object OptionalLimitQueryParamMatcher extends OptionalQueryParamDecoderMatcher[Int]("limit")

    object OptionalCategoryQueryParamMatcher extends OptionalQueryParamDecoderMatcher[Category]("category")

    implicit val orderByQueryParamDecoder: QueryParamDecoder[List[SortBy]] =
      QueryParamDecoder[String].map(OrderBy.fromString)

    object OptionalOrderByQueryParamMatcher extends OptionalQueryParamDecoderMatcher[List[SortBy]]("sort_by")

    @annotation.tailrec
    def isValidOrderByForItem(orderBy: Seq[SortBy]): Boolean =
      orderBy match {
        case s :: xs ⇒
          refineV[Item.SortableField](s.name.value) match {
            case Right(_) ⇒ isValidOrderByForItem(xs)
            case Left(_)  ⇒ false
          }

        case _ ⇒ true
      }

    def list(maybeCategory: Option[Category], orderBy: List[SortBy], page: Page) =
      listItems[F](maybeCategory, orderBy, page) >>= { items ⇒
        Ok(items.asJson)
      }

    HttpRoutes.of[F] {
      case GET → Root :? OptionalCategoryQueryParamMatcher(maybeCategory) +& OptionalOrderByQueryParamMatcher(
            maybeOrderBy
          ) +& OptionalMarkerQueryParamMatcher(marker) +& OptionalLimitQueryParamMatcher(limit) ⇒
        val orderBy = maybeOrderBy.getOrElse(Nil)

        if (isValidOrderByForItem(orderBy)) {

          limit match {
            case Some(n) ⇒
              refineV[Page.Limit](n) match {
                case Right(n) ⇒ list(maybeCategory, orderBy, Page(marker, Some(n)))
                case _        ⇒ BadRequest(s"Invalid value for limit (max: 512): $n")
              }

            case None ⇒ list(maybeCategory, orderBy, Page(marker, None))
          }

        } else BadRequest(s"Invalid value for `sort_by` parameter: $orderBy")

    }
  }

  private def deleteItemEndpoint(): HttpRoutes[F] =
    HttpRoutes.of[F] {
      case DELETE → (Root / ItemId(id)) ⇒
        for {
          _ ← deleteItem[F](id)
          resp ← NoContent()
        } yield resp
    }

  private def getItemEndpoint(): HttpRoutes[F] =
    HttpRoutes.of[F] {
      case GET → (Root / ItemId(id)) ⇒
        for {
          item ← getItem[F](id)
          resp ← Ok(item.asJson)
        } yield resp
    }

  private def createStockEntryEndpoint(): HttpRoutes[F] = {
    implicit val deltaQueryParamDecoder: QueryParamDecoder[Delta] =
      QueryParamDecoder[Int].map(Delta(_))

    object DeltaQueryParamMatcher extends ValidatingQueryParamDecoderMatcher[Delta]("delta")

    HttpRoutes.of[F] {
      case PUT → (Root / ItemId(itemId) / "stocks") :? DeltaQueryParamMatcher(deltaValidated) ⇒
        deltaValidated.fold(
          _ ⇒ BadRequest("unable to parse argument `delta`"),
          value ⇒
            for {
              stock ← createEntry[F](itemId, value)
              resp ← Ok(stock.asJson)
            } yield resp
        )

    }
  }

  private def getStockEndpoint(): HttpRoutes[F] =
    HttpRoutes.of[F] {
      case GET → (Root / ItemId(itemId) / "stocks") ⇒
        for {
          stock ← getStock[F](itemId)
          resp ← Ok(stock.asJson)
        } yield resp
    }

  private def getSwaggerSpec(swaggerConf: SwaggerConf): HttpRoutes[F] =
    HttpRoutes.of[F] {
      case GET → (Root / "swagger-spec.json") ⇒
        Ok(SwaggerSpec.swaggerSpec(swaggerConf))
    }

  def endpoints(swaggerConf: SwaggerConf)(
      implicit H: HttpErrorHandler[F, AppError],
  ): HttpRoutes[F] =
    H.handle {
      createEndpoint() <+>
        patchEndpoint() <+>
        updateEndpoint() <+>
        deleteItemEndpoint() <+>
        getItemEndpoint() <+>
        listEndpoint() <+>
        createStockEntryEndpoint() <+>
        getStockEndpoint() <+>
        getSwaggerSpec(swaggerConf)
    }
}

object ItemEndpoints {
  def endpoints[F[_]: Sync: Clock: ApplicativeAsk[?[_], AppConf]: HttpErrorHandler[?[_], AppError]](
      implicit idA: ApplicativeAsk[F, Id[F]],
      itemsA: ApplicativeAsk[F, Items[F]],
      stocksA: ApplicativeAsk[F, Stocks[F]]
  ): F[HttpRoutes[F]] =
    for {
      swaggerConf ← AppConf.swaggerConf[F]
    } yield new ItemEndpoints[F].endpoints(swaggerConf)

  final case class ItemRequest(
      name: String,
      priceInCents: Int,
      currency: String,
      category: String,
  ) {

    def validate[F[_]: Clock: Sync]: F[(Name, Money, Category)] = {
      import Validation._
      import cats.data.ValidatedNec
      import eu.timepit.refined.collection.NonEmpty
      import eu.timepit.refined.numeric.NonNegative

      val item: ValidatedNec[FieldError, (Name, Money, Category)] = Apply[ValidatedNec[FieldError, ?]]
        .map4(
          refineV[NonEmpty](name)
            .leftMap(_ ⇒ FieldError("item.name", "error.empty"))
            .toValidatedNec,
          refineV[NonNegative](priceInCents)
            .leftMap(_ ⇒ FieldError("item.priceInCents", "error.negative"))
            .toValidatedNec,
          refineV[Money.CurrencyCode](currency)
            .leftMap(_ ⇒ FieldError("item.currency", "error.currency"))
            .toValidatedNec,
          refineV[NonEmpty](category)
            .leftMap(_ ⇒ FieldError("item.category", "error.empty"))
            .toValidatedNec,
        ) { (name, priceInCents, currency, category) ⇒
          (
            Name(name),
            Money(priceInCents, currency),
            Category(category),
          )
        }

      item.fold(errors ⇒ Sync[F].raiseError(AppError.validationFailed(errors)), Sync[F].pure)
    }
  }

  object ItemRequest {

    def fromItem(item: Item) =
      ItemRequest(
        name = item.name.value,
        priceInCents = item.price.amountInCents,
        currency = item.price.currency,
        category = item.category.value,
      )

  }

}
