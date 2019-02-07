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
import service.{ IdService, ItemService, StockService }
import utils.Validation
import eu.timepit.refined._
import eu.timepit.refined.auto._

import java.util.concurrent.TimeUnit
import java.time.Instant

import cats.mtl.ApplicativeAsk

final class ItemEndpoints[F[_]: Sync: Clock] extends Http4sDsl[F] with Codecs {
  import ItemEndpoints._

  private def createEndpoint(itemService: ItemService[F], idService: IdService[F]): HttpRoutes[F] =
    HttpRoutes.of[F] {
      case req @ POST → Root ⇒
        for {
          itemRequest ← req.as[ItemRequest]

          (name, price, category) ← itemRequest.validate

          newId ← idService.newItemId

          now ← Clock[F].monotonic(TimeUnit.MILLISECONDS)

          item = Item(
            name = name,
            price = price,
            category = category,
            createdAt = DateTime(Instant.ofEpochMilli(now)),
            updatedAt = DateTime(Instant.ofEpochMilli(now)),
            id = newId,
          )

          _ ← itemService.createItem(item)

          resp ← Created(item.asJson)
        } yield resp

    }

  private def updateEndpoint(itemService: ItemService[F]): HttpRoutes[F] =
    HttpRoutes.of[F] {
      case req @ PUT → (Root / ItemId(id)) ⇒
        for {
          item ← itemService.getItem(id)

          itemRequest ← req.as[ItemRequest]

          (name, price, category) ← itemRequest.validate

          now ← Clock[F].monotonic(TimeUnit.MILLISECONDS)

          updated = item.copy(
            name = name,
            price = price,
            category = category,
            updatedAt = DateTime(Instant.ofEpochMilli(now))
          )

          _ ← itemService.update(updated)

          resp ← Ok(updated.asJson)
        } yield resp

    }

  private def patchEndpoint(itemService: ItemService[F]): HttpRoutes[F] = {

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

          item ← itemService.getItem(id)

          itemRequest ← {
            val unitR = ItemRequest.fromItem(item)

            val result = patches.foldLeft(unitR.asJson) { (input, patch) ⇒
              patch.applyOperation(input)
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
          _ ← itemService.update(updated)

          resp ← Ok(updated.asJson)
        } yield resp

    }
  }

  private def listEndpoint(itemService: ItemService[F]): HttpRoutes[F] = {
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

    HttpRoutes.of[F] {
      case GET → Root :? OptionalCategoryQueryParamMatcher(maybeCategory) +& OptionalOrderByQueryParamMatcher(
            maybeOrderBy
          ) +& OptionalMarkerQueryParamMatcher(marker) +& OptionalLimitQueryParamMatcher(limit) ⇒
        val orderBy = maybeOrderBy.getOrElse(Nil)

        if (isValidOrderByForItem(orderBy)) {
          val items = itemService
            .list(maybeCategory, orderBy, Page(marker, limit))

          Ok(
            fs2.Stream("[") ++
              items
                .map(_.asJson.noSpaces)
                .intersperse(",") ++ fs2.Stream("]")
          )
        } else BadRequest(s"Invalid value for `sort_by` parameter: $orderBy")

    }
  }

  private def deleteItemEndpoint(itemService: ItemService[F]): HttpRoutes[F] =
    HttpRoutes.of[F] {
      case DELETE → (Root / ItemId(id)) ⇒
        for {
          _ ← itemService.deleteItem(id)
          resp ← NoContent()
        } yield resp
    }

  private def getItemEndpoint(itemService: ItemService[F]): HttpRoutes[F] =
    HttpRoutes.of[F] {
      case GET → (Root / ItemId(id)) ⇒
        for {
          item ← itemService.getItem(id)
          resp ← Ok(item.asJson)
        } yield resp
    }

  private def createStockEntryEndpoint(stockService: StockService[F]): HttpRoutes[F] = {
    implicit val deltaQueryParamDecoder: QueryParamDecoder[Delta] =
      QueryParamDecoder[Int].map(Delta(_))

    object DeltaQueryParamMatcher extends ValidatingQueryParamDecoderMatcher[Delta]("delta")

    HttpRoutes.of[F] {
      case PUT → (Root / ItemId(itemId) / "stocks") :? DeltaQueryParamMatcher(deltaValidated) ⇒
        deltaValidated.fold(
          _ ⇒ BadRequest("unable to parse argument `delta`"),
          value ⇒
            for {
              stock ← stockService.createEntry(itemId, value)
              resp ← Ok(stock.asJson)
            } yield resp
        )

    }
  }

  private def getStockEndpoint(stockService: StockService[F]): HttpRoutes[F] =
    HttpRoutes.of[F] {
      case GET → (Root / ItemId(itemId) / "stocks") ⇒
        for {
          stock ← stockService.getStock(itemId)
          resp ← Ok(stock.asJson)
        } yield resp
    }

  private def getSwaggerSpec(swaggerConf: SwaggerConf): HttpRoutes[F] =
    HttpRoutes.of[F] {
      case GET → (Root / "swagger-spec.json") ⇒
        Ok(SwaggerSpec.swaggerSpec(swaggerConf))
    }

  def endpoints(itemService: ItemService[F],
                stockService: StockService[F],
                idService: IdService[F],
                swaggerConf: SwaggerConf)(
      implicit H: HttpErrorHandler[F, AppError],
  ): HttpRoutes[F] =
    H.handle {
      createEndpoint(itemService, idService) <+>
        patchEndpoint(itemService) <+>
        updateEndpoint(itemService) <+>
        deleteItemEndpoint(itemService) <+>
        getItemEndpoint(itemService) <+>
        listEndpoint(itemService) <+>
        createStockEntryEndpoint(stockService) <+>
        getStockEndpoint(stockService) <+>
        getSwaggerSpec(swaggerConf)
    }
}

object ItemEndpoints {
  def endpoints[F[_]: Sync: Clock: ApplicativeAsk[?[_], AppConf]](itemService: ItemService[F],
                                                                  stockService: StockService[F],
                                                                  idService: IdService[F],
  )(
      implicit H: HttpErrorHandler[F, AppError]
  ): F[HttpRoutes[F]] =
    for {
      swaggerConf ← AppConf.swaggerConf[F]
    } yield new ItemEndpoints[F].endpoints(itemService, stockService, idService, swaggerConf)

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
