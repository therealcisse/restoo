package name.amadoucisse.restoo
package infra
package endpoint

import scala.language.higherKinds
import cats.effect.Sync
import cats.implicits._
import cats.Apply
import io.circe.generic.auto._
import io.circe.syntax._
import org.http4s.circe._
import org.http4s.dsl.Http4sDsl
import org.http4s.{ EntityDecoder, HttpRoutes, QueryParamDecoder }
import domain._
import domain.items._
import domain.entries._
import config.{ AppConf, SwaggerConf }
import http.{ HttpErrorHandler, JsonPatch, OrderBy, SortBy, SwaggerSpec }
import service.{ ItemService, StockService }
import utils.Validation
import eu.timepit.refined._
import eu.timepit.refined.auto._

import cats.mtl.ApplicativeAsk

final class ItemEndpoints[F[_]](implicit F: Sync[F]) extends Http4sDsl[F] {
  import ItemEndpoints._

  implicit val itemRequestDecoder: EntityDecoder[F, ItemRequest] = jsonOf

  implicit val stockRequestDecoder: EntityDecoder[F, StockRequest] = jsonOf

  implicit val jsonPatchDecoder: EntityDecoder[F, Vector[JsonPatch]] = jsonOf

  private def createEndpoint(itemService: ItemService[F]): HttpRoutes[F] =
    HttpRoutes.of[F] {
      case req @ POST → Root ⇒
        for {
          itemRequest ← req.as[ItemRequest]

          item ← itemRequest.validate

          result ← itemService.createItem(item)

          resp ← Created(result.asJson)
        } yield resp

    }

  private def updateEndpoint(itemService: ItemService[F]): HttpRoutes[F] =
    HttpRoutes.of[F] {
      case req @ PUT → (Root / ItemId(id)) ⇒
        for {
          itemRequest ← req.as[ItemRequest]

          item ← itemRequest.validate

          toUpdate = item.copy(id = id.some)
          result ← itemService.update(toUpdate)

          resp ← Ok(result.asJson)
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
            .ensureOr(_ ⇒ AppError.invalidJsonPatch) { patches ⇒
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
                F.delay(scribe.error(s"Error : ${r.message}. Could not create request from json patch : $patches")) *> F
                  .raiseError(AppError.invalidJsonPatch),
              F.pure
            )
          }

          item ← itemRequest.validate

          updated = item.copy(id = id.some)
          result ← itemService.update(updated)

          resp ← Ok(result.asJson)
        } yield resp

    }
  }

  private def listEndpoint(itemService: ItemService[F]): HttpRoutes[F] = {
    implicit val categoryQueryParamDecoder: QueryParamDecoder[Category] =
      QueryParamDecoder[String].map(Category(_))

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
      case GET → Root :? OptionalCategoryQueryParamMatcher(maybeCategory) :? OptionalOrderByQueryParamMatcher(
            maybeOrderBy
          ) ⇒
        val orderBy = maybeOrderBy.getOrElse(Nil)

        if (isValidOrderByForItem(orderBy)) {
          val items = itemService
            .list(maybeCategory, orderBy)

          Ok(
            fs2.Stream("[") ++
              items
                .map(_.asJson.noSpaces)
                .intersperse(",") ++ fs2.Stream("]")
          )
        } else F.delay(scribe.error(s"Invalid order by param : $orderBy")) *> BadRequest()

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

  private def createStockEntryEndpoint(stockService: StockService[F]): HttpRoutes[F] =
    HttpRoutes.of[F] {
      case req @ PUT → (Root / ItemId(itemId) / "stocks") ⇒
        for {
          stockRequest ← req.as[StockRequest]
          stock ← stockService.createEntry(itemId, Delta(stockRequest.delta))
          resp ← Ok(stock.asJson)
        } yield resp
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

  def endpoints(itemService: ItemService[F], stockService: StockService[F], swaggerConf: SwaggerConf)(
      implicit H: HttpErrorHandler[F, AppError],
  ): HttpRoutes[F] =
    H.handle {
      createEndpoint(itemService) <+>
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
  def endpoints[F[_]: Sync: ApplicativeAsk[?[_], AppConf]](itemService: ItemService[F], stockService: StockService[F])(
      implicit H: HttpErrorHandler[F, AppError]
  ): F[HttpRoutes[F]] =
    for {
      swaggerConf ← AppConf.swaggerConf[F]
    } yield new ItemEndpoints[F].endpoints(itemService, stockService, swaggerConf)

  final case class ItemRequest(
      name: String,
      priceInCents: Int,
      currency: String,
      category: String,
  ) {

    def validate[F[_]](implicit F: Sync[F]): F[Item] = {
      import Validation._
      import cats.data.ValidatedNec
      import eu.timepit.refined.collection.NonEmpty
      import eu.timepit.refined.numeric.NonNegative

      val item: ValidatedNec[FieldError, Item] = Apply[ValidatedNec[FieldError, ?]]
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
          Item(
            name = Name(name),
            price = Money(priceInCents, currency),
            category = Category(category),
          )
        }

      item.fold(errors ⇒ F.raiseError(AppError.validationFailed(errors)), _.pure[F])
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

  final case class StockRequest(delta: Int) extends AnyVal
}
