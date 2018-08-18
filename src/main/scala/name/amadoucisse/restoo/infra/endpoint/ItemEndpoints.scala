package name.amadoucisse.restoo
package infra
package endpoint

import scala.language.higherKinds
import cats.effect.Effect
import cats.data.EitherT
import cats.implicits._
import io.circe.generic.auto._
import io.circe.syntax._
import org.http4s.circe._
import org.http4s.dsl.Http4sDsl
import org.http4s.{EntityDecoder, HttpService, QueryParamDecoder}
import domain._
import domain.items._
import domain.entries._
import config.SwaggerConf
import http.{HttpErrorHandler, JsonPatch, OrderBy, SortBy, SwaggerSpec}
import service.{ItemService, StockService}
import utils.Validator

final class ItemEndpoints[F[_]: Effect](implicit httpErrorHandler: HttpErrorHandler[F])
    extends Http4sDsl[F] {
  import ItemEndpoints._

  implicit val itemRequestDecoder: EntityDecoder[F, ItemRequest] = jsonOf

  implicit val stockRequestDecoder: EntityDecoder[F, StockRequest] = jsonOf

  implicit val jsonPatchDecoder: EntityDecoder[F, Vector[JsonPatch]] = jsonOf

  private def createEndpoint(itemService: ItemService[F]): HttpService[F] =
    HttpService[F] {
      case req @ POST -> Root =>
        val action = for {
          itemRequest <- EitherT.right[AppError](req.as[ItemRequest])

          item <- EitherT.fromEither[F](itemRequest.validate)

          result <- EitherT(itemService.createItem(item))

        } yield result

        for {
          item <- action.value
          resp <- item.fold(httpErrorHandler.handle, item => Created(item.asJson))
        } yield resp
    }

  private def updateEndpoint(itemService: ItemService[F]): HttpService[F] =
    HttpService[F] {
      case req @ PUT -> Root / ItemId(id) =>
        val action = for {
          itemRequest <- EitherT.right[AppError](req.as[ItemRequest])

          item <- EitherT.fromEither[F](itemRequest.validate)

          updated = item.copy(id = id.some)
          result <- EitherT(itemService.update(updated))

        } yield result

        for {
          item <- action.value
          resp <- item.fold(httpErrorHandler.handle, item => Ok(item.asJson))
        } yield resp
    }

  private def patchEndpoint(itemService: ItemService[F]): HttpService[F] =
    HttpService[F] {
      case req @ PATCH -> Root / ItemId(id) =>
        val action = for {

          // Accepts object or array of objects
          patches <- EitherT
            .right[AppError](req.as[Vector[JsonPatch]])
            .ensureOr(_ => AppError.invalidJsonPatch("Invalid patch"))(_.nonEmpty)

          item <- EitherT(itemService.getItem(id))

          itemRequest <- EitherT.fromEither[F] {
            val unitR = ItemRequest.fromItem(item)

            patches
              .foldLeft(unitR.asJson) { (input, patch) =>
                patch.applyOperation(input)
              }
              .as[ItemRequest]
              .leftMap(r => AppError.invalidJsonPatch(r.message))
          }

          item <- EitherT.fromEither[F](itemRequest.validate)

          updated = item.copy(id = id.some)
          result <- EitherT(itemService.update(updated))

        } yield result

        for {
          item <- action.value
          resp <- item.fold(httpErrorHandler.handle, item => Ok(item.asJson))
        } yield resp
    }

  implicit val categoryQueryParamDecoder: QueryParamDecoder[Category] =
    QueryParamDecoder[String].map(Category(_))

  object OptionalCategoryQueryParamMatcher
      extends OptionalQueryParamDecoderMatcher[Category]("category")

  implicit val orderByQueryParamDecoder: QueryParamDecoder[Seq[SortBy]] =
    QueryParamDecoder[String].map(OrderBy.fromString)

  object OptionalOrderByQueryParamMatcher
      extends OptionalQueryParamDecoderMatcher[Seq[SortBy]]("sort_by")

  private def listEndpoint(itemService: ItemService[F]): HttpService[F] =
    HttpService[F] {
      case GET -> Root :? OptionalCategoryQueryParamMatcher(maybeCategory) :? OptionalOrderByQueryParamMatcher(
            maybeOrderBy) =>
        val items = itemService
          .list(maybeCategory, maybeOrderBy.getOrElse(Nil))

        Ok(
          fs2.Stream("[") ++
            items
              .map(_.asJson.noSpaces)
              .intersperse(",") ++ fs2
            .Stream("]"))
    }

  private def deleteItemEndpoint(itemService: ItemService[F]): HttpService[F] =
    HttpService[F] {
      case DELETE -> Root / ItemId(id) =>
        for {
          _ <- itemService.deleteItem(id)
          resp <- Ok()
        } yield resp
    }

  private def getItemEndpoint(itemService: ItemService[F]): HttpService[F] =
    HttpService[F] {
      case GET -> Root / ItemId(id) =>
        for {
          item <- itemService.getItem(id)
          resp <- item.fold(httpErrorHandler.handle, item => Ok(item.asJson))
        } yield resp
    }

  private def createStockEntryEndpoint(stockService: StockService[F]): HttpService[F] =
    HttpService[F] {
      case req @ PUT -> Root / ItemId(itemId) / "stocks" =>
        for {
          stockRequest <- req.as[StockRequest]
          entry <- stockService.createEntry(itemId, Delta(stockRequest.delta)).value
          resp <- entry.fold(httpErrorHandler.handle, entry => Ok(entry.asJson))
        } yield resp

    }

  private def getStockEndpoint(stockService: StockService[F]): HttpService[F] =
    HttpService[F] {
      case GET -> Root / ItemId(itemId) / "stocks" =>
        for {
          stock <- stockService.getStock(itemId)
          resp <- stock.fold(httpErrorHandler.handle, stock => Ok(stock.asJson))
        } yield resp
    }

  private def getSwaggerSpec(swaggerConf: SwaggerConf): HttpService[F] =
    HttpService[F] {
      case GET -> Root / "swagger-spec.json" =>
        Ok(SwaggerSpec.swaggerSpec(swaggerConf))
    }

  @SuppressWarnings(Array("org.wartremover.warts.Any"))
  def endpoints(
      itemService: ItemService[F],
      stockService: StockService[F],
      swaggerConf: SwaggerConf): HttpService[F] =
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

object ItemEndpoints {
  def endpoints[F[_]: Effect](
      itemService: ItemService[F],
      stockService: StockService[F],
      swaggerConf: SwaggerConf,
  )(implicit httpErrorHandler: HttpErrorHandler[F]): HttpService[F] =
    new ItemEndpoints[F].endpoints(itemService, stockService, swaggerConf)

  final case class ItemRequest(name: String, price: Double, category: String) {
    import Validator._

    def validate: AppError Either Item = {
      val item = (
        validateNonEmpty(name, FieldError("item.name", "error.empty")),
        validateNonNegative(price, FieldError("item.price", "error.negative")),
        validateNonEmpty(category, FieldError("item.category", "error.empty"))).mapN {
        (name, price, category) =>
          Item(
            name = Name(name),
            priceInCents = Cents.fromStandardAmount(price),
            category = Category(category),
          )
      }

      item.leftMap(AppError.invalidEntity).toEither
    }
  }

  object ItemRequest {

    def fromItem(item: Item) =
      ItemRequest(
        name = item.name.value,
        price = item.priceInCents.toDouble,
        category = item.category.value,
      )

  }

  final case class StockRequest(delta: Int) extends AnyVal
}
