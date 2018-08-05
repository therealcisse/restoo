package name.amadoucisse.restoo
package infra
package endpoint

import scala.language.higherKinds
import cats.effect.Effect
import cats.data.EitherT
import cats.implicits._
import io.circe.Json
import io.circe.generic.auto._
import io.circe.syntax._
import org.http4s.circe._
import org.http4s.dsl.Http4sDsl
import org.http4s.{EntityDecoder, HttpService}
import domain._
import domain.items._
import domain.entries._
import config.SwaggerConf
import http.HttpErrorHandler
import service.{ItemService, StockService}

final class ItemEndpoints[F[_]: Effect](implicit httpErrorHandler: HttpErrorHandler[F])
    extends Http4sDsl[F] {
  import ItemEndpoints._

  implicit val itemRequestDecoder: EntityDecoder[F, ItemRequest] = jsonOf

  implicit val stockRequestDecoder: EntityDecoder[F, StockRequest] = jsonOf

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
          resp <- item.fold(httpErrorHandler.handle, item => Ok(item.asJson))
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

  private def listEndpoint(itemService: ItemService[F]): HttpService[F] =
    HttpService[F] {
      case GET -> Root =>
        Ok(
          fs2.Stream("[") ++ itemService.list().map(_.asJson.noSpaces).intersperse(",") ++ fs2
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
        Ok(ItemEndpoints.swaggerSpec(swaggerConf))
    }

  @SuppressWarnings(Array("org.wartremover.warts.Any"))
  def endpoints(
      itemService: ItemService[F],
      stockService: StockService[F],
      swaggerConf: SwaggerConf): HttpService[F] =
    createEndpoint(itemService) <+>
      updateEndpoint(itemService) <+>
      deleteItemEndpoint(itemService) <+>
      getItemEndpoint(itemService) <+>
      listEndpoint(itemService) <+>
      createStockEntryEndpoint(stockService) <+>
      getStockEndpoint(stockService) <+>
      getSwaggerSpec(swaggerConf)
}

object ItemEndpoints {
  val ApiVersion = "v1"

  def endpoints[F[_]: Effect](
      itemService: ItemService[F],
      stockService: StockService[F],
      swaggerConf: SwaggerConf,
  )(implicit httpErrorHandler: HttpErrorHandler[F]): HttpService[F] =
    new ItemEndpoints[F].endpoints(itemService, stockService, swaggerConf)

  final case class ItemRequest(name: String, price: Double, category: String) {
    import utils.Validator._

    def validate: Either[AppError, Item] = {
      val item = (
        validateNonEmpty(name, FieldError("item.name", "error.empty")),
        validateNonNegative(price, FieldError("item.price", "error.negative")),
        validateNonEmpty(category, FieldError("item.category", "error.empty"))).mapN {
        (name, price, category) =>
          Item(
            name = Name(name),
            priceInCents = Cents(price),
            category = Category(category)
          )
      }

      item.leftMap(ErrorListing).toEither
    }
  }

  final case class StockRequest(delta: Int) extends AnyVal

  // TODO: Use Rho
  private def swaggerSpec(swaggerConf: SwaggerConf): Json = Json.obj(
    "swagger" -> Json.fromString("2.0"),
    "info" -> Json.obj(
      "description" -> Json.fromString("REST API for managing restaurant stock.")
    ),
    "host" -> Json.fromString(swaggerConf.host),
    "basePath" -> Json.fromString(s"/api/$ApiVersion/items"),
    "schemes" -> Json.arr(swaggerConf.schemes.map(Json.fromString): _*),
    "paths" -> Json.obj(
      "" -> Json.obj(
        "get" -> Json.obj(
          "summary" -> Json.fromString("Get a list of items"),
          "description" -> Json.fromString(""),
          "operationId" -> Json.fromString("listitems"),
          "consumes" -> Json.arr(Json.fromString("application/json")),
          "produces" -> Json.arr(Json.fromString("application/json")),
          "parameters" -> Json.arr(),
          "responses" -> Json.obj("200" -> Json.obj(
            "description" -> Json.fromString("Success"),
            "schema" -> Json.obj(
              "type" -> Json.fromString("array"),
              "items" -> Json.obj(
                "$ref" -> Json.fromString("#/definitions/Item")
              )
            ),
          ))
        ),
        "post" -> Json.obj(
          "summary" -> Json.fromString("Create an item"),
          "description" -> Json.fromString(""),
          "operationId" -> Json.fromString("createItem"),
          "consumes" -> Json.arr(Json.fromString("application/json")),
          "produces" -> Json.arr(Json.fromString("application/json")),
          "parameters" -> Json.arr(Json.obj(
            "in" -> Json.fromString("body"),
            "name" -> Json.fromString("body"),
            "description" -> Json.fromString("ItemRequest object"),
            "required" -> Json.fromBoolean(true),
            "schema" -> Json.obj(
              "$ref" -> Json.fromString("#/definitions/ItemRequest")
            )
          )),
          "responses" -> Json.obj(
            "200" -> Json.obj(
              "description" -> Json.fromString("Success"),
              "schema" -> Json.obj(
                "$ref" -> Json.fromString("#/definitions/Item")
              ),
            ),
            "409" -> Json.obj(
              "description" -> Json.fromString("Item already exists"),
              "schema" -> Json.obj(
                "$ref" -> Json.fromString("#/definitions/ApiResponseWrapper")
              ),
            ),
            "422" -> Json.obj(
              "description" -> Json.fromString("Validation error"),
              "schema" -> Json.obj(
                "$ref" -> Json.fromString("#/definitions/FieldErrors"),
              ),
            ),
          )
        ),
      ),
      "/{itemId}" -> Json.obj(
        "get" -> Json.obj(
          "summary" -> Json.fromString("Get single item"),
          "description" -> Json.fromString(""),
          "operationId" -> Json.fromString("getItem"),
          "consumes" -> Json.arr(),
          "produces" -> Json.arr(Json.fromString("application/json")),
          "parameters" -> Json.arr(Json.obj(
            "in" -> Json.fromString("path"),
            "name" -> Json.fromString("itemId"),
            "description" -> Json.fromString("Id of the item."),
            "type" -> Json.fromString("integer"),
            "format" -> Json.fromString("int32"),
            "required" -> Json.fromBoolean(true),
          )),
          "responses" -> Json.obj(
            "200" -> Json.obj(
              "description" -> Json.fromString("Success"),
              "schema" -> Json.obj(
                "$ref" -> Json.fromString("#/definitions/Item")
              ),
            ),
            "404" -> Json.obj(
              "description" -> Json.fromString("Item not found"),
              "schema" -> Json.obj(
                "$ref" -> Json.fromString("#/definitions/ApiResponseWrapper")
              ),
            ),
          )
        ),
        "put" -> Json.obj(
          "summary" -> Json.fromString("Update an item"),
          "description" -> Json.fromString(""),
          "operationId" -> Json.fromString("updateItem"),
          "consumes" -> Json.arr(Json.fromString("application/json")),
          "produces" -> Json.arr(Json.fromString("application/json")),
          "parameters" -> Json.arr(
            Json.obj(
              "in" -> Json.fromString("path"),
              "name" -> Json.fromString("itemId"),
              "description" -> Json.fromString("Id of the item."),
              "type" -> Json.fromString("integer"),
              "format" -> Json.fromString("int32"),
              "required" -> Json.fromBoolean(true),
            ),
            Json.obj(
              "in" -> Json.fromString("body"),
              "name" -> Json.fromString("body"),
              "description" -> Json.fromString("Item object"),
              "required" -> Json.fromBoolean(true),
              "schema" -> Json.obj(
                "$ref" -> Json.fromString("#/definitions/ItemRequest")
              )
            )
          ),
          "responses" -> Json.obj(
            "200" -> Json.obj(
              "description" -> Json.fromString("Success"),
              "schema" -> Json.obj(
                "$ref" -> Json.fromString("#/definitions/Item")
              ),
            ),
            "404" -> Json.obj(
              "description" -> Json.fromString("Item not found"),
              "schema" -> Json.obj(
                "$ref" -> Json.fromString("#/definitions/ApiResponseWrapper")
              ),
            ),
            "422" -> Json.obj(
              "description" -> Json.fromString("Validation error"),
              "schema" -> Json.obj(
                "$ref" -> Json.fromString("#/definitions/FieldErrors"),
              ),
            ),
          ),
        ),
        "delete" -> Json.obj(
          "summary" -> Json.fromString("Delete an item"),
          "description" -> Json.fromString(""),
          "operationId" -> Json.fromString("deleteItem"),
          "consumes" -> Json.arr(),
          "produces" -> Json.arr(Json.fromString("application/json")),
          "parameters" -> Json.arr(Json.obj(
            "in" -> Json.fromString("path"),
            "name" -> Json.fromString("itemId"),
            "description" -> Json.fromString("Id of the item."),
            "type" -> Json.fromString("integer"),
            "format" -> Json.fromString("int64"),
            "required" -> Json.fromBoolean(true),
          )),
          "responses" -> Json.obj(
            "200" -> Json.obj(
              "description" -> Json.fromString("Success"),
            ))
        ),
      ),
      "/{itemId}/stocks" -> Json.obj(
        "get" -> Json.obj(
          "summary" -> Json.fromString("Get item stock"),
          "description" -> Json.fromString(""),
          "operationId" -> Json.fromString("getItemStock"),
          "consumes" -> Json.arr(),
          "produces" -> Json.arr(Json.fromString("application/json")),
          "parameters" -> Json.arr(Json.obj(
            "in" -> Json.fromString("path"),
            "name" -> Json.fromString("itemId"),
            "description" -> Json.fromString("Id of the item."),
            "type" -> Json.fromString("integer"),
            "required" -> Json.fromBoolean(true),
          )),
          "responses" -> Json.obj(
            "200" -> Json.obj(
              "description" -> Json.fromString("Success"),
              "schema" -> Json.obj(
                "$ref" -> Json.fromString("#/definitions/Stock")
              ),
            ),
            "404" -> Json.obj(
              "description" -> Json.fromString("Item not found"),
              "schema" -> Json.obj(
                "$ref" -> Json.fromString("#/definitions/ApiResponseWrapper")
              ),
            ),
          )
        ),
        "put" -> Json.obj(
          "summary" -> Json.fromString("Update item stock"),
          "description" -> Json.fromString(""),
          "operationId" -> Json.fromString("updateItemStock"),
          "consumes" -> Json.arr(Json.fromString("application/json")),
          "produces" -> Json.arr(Json.fromString("application/json")),
          "parameters" -> Json.arr(
            Json.obj(
              "in" -> Json.fromString("path"),
              "name" -> Json.fromString("itemId"),
              "description" -> Json.fromString("Id of the item."),
              "type" -> Json.fromString("integer"),
              "format" -> Json.fromString("int32"),
              "required" -> Json.fromBoolean(true),
            ),
            Json.obj(
              "in" -> Json.fromString("body"),
              "name" -> Json.fromString("body"),
              "description" -> Json.fromString("Delta object"),
              "required" -> Json.fromBoolean(true),
              "schema" -> Json.obj(
                "$ref" -> Json.fromString("#/definitions/Delta")
              )
            )
          ),
          "responses" -> Json.obj(
            "200" -> Json.obj(
              "description" -> Json.fromString("Success"),
              "schema" -> Json.obj(
                "$ref" -> Json.fromString("#/definitions/Stock")
              ),
            ),
            "404" -> Json.obj(
              "description" -> Json.fromString("Item not found"),
              "schema" -> Json.obj(
                "$ref" -> Json.fromString("#/definitions/ApiResponseWrapper")
              ),
            ),
            "409" -> Json.obj(
              "description" -> Json.fromString("Item out of stock"),
              "schema" -> Json.obj(
                "$ref" -> Json.fromString("#/definitions/ApiResponseWrapper")
              ),
            ),
          )
        ),
      ),
    ),
    "definitions" -> Json.obj(
      "ItemRequest" -> Json.obj(
        "type" -> Json.fromString("object"),
        "required" -> Json.arr(
          Json.fromString("name"),
          Json.fromString("price"),
          Json.fromString("category"),
        ),
        "properties" -> Json.obj(
          "name" -> Json.obj(
            "type" -> Json.fromString("string"),
            "minLength" -> Json.fromInt(1),
          ),
          "price" -> Json.obj(
            "type" -> Json.fromString("number"),
            "format" -> Json.fromString("double"),
            "minimum" -> Json.fromDoubleOrNull(0.0),
          ),
          "category" -> Json.obj(
            "type" -> Json.fromString("string"),
          ),
        )
      ),
      "Delta" -> Json.obj(
        "type" -> Json.fromString("object"),
        "required" -> Json
          .arr(Json.fromString("delta")),
        "properties" -> Json.obj(
          "delta" -> Json.obj(
            "type" -> Json.fromString("integer"),
            "format" -> Json.fromString("int32"),
          ),
        )
      ),
      "Stock" -> Json.obj(
        "type" -> Json.fromString("object"),
        "required" -> Json.arr(
          Json.fromString("item"),
          Json.fromString("quantity")
        ),
        "properties" -> Json.obj(
          "item" -> Json.obj(
            "$ref" -> Json.fromString("#/definitions/Item")
          ),
          "quantity" -> Json.obj(
            "type" -> Json.fromString("integer"),
            "format" -> Json.fromString("int64"),
          ),
        )
      ),
      "ApiResponseWrapper" -> Json.obj(
        "type" -> Json.fromString("object"),
        "required" -> Json.arr(
          Json.fromString("error"),
        ),
        "properties" -> Json.obj(
          "error" -> Json.obj(
            "$ref" -> Json.fromString("#/definitions/ApiResponse")
          ),
        )
      ),
      "FieldError" -> Json.obj(
        "type" -> Json.fromString("object"),
        "required" -> Json.arr(
          Json.fromString("id"),
          Json.fromString("message"),
          Json.fromString("type"),
        ),
        "properties" -> Json.obj(
          "id" -> Json.obj(
            "type" -> Json.fromString("string"),
          ),
          "message" -> Json.obj(
            "type" -> Json.fromString("string"),
          ),
          "type" -> Json.obj(
            "type" -> Json.fromString("string"),
          ),
        )
      ),
      "FieldErrors" -> Json.obj(
        "type" -> Json.fromString("object"),
        "required" -> Json.arr(
          Json.fromString("code"),
          Json.fromString("message"),
          Json.fromString("type"),
          Json.fromString("errors"),
        ),
        "properties" -> Json.obj(
          "code" -> Json.obj(
            "type" -> Json.fromString("string"),
          ),
          "message" -> Json.obj(
            "type" -> Json.fromString("string"),
          ),
          "type" -> Json.obj(
            "type" -> Json.fromString("string"),
          ),
          "errors" -> Json.obj(
            "type" -> Json.fromString("array"),
            "items" -> Json.obj("$ref" -> Json.fromString("#/definitions/FieldError")),
          ),
        )
      ),
      "ApiResponse" -> Json.obj(
        "type" -> Json.fromString("object"),
        "required" -> Json.arr(
          Json.fromString("code"),
          Json.fromString("message"),
          Json.fromString("type"),
        ),
        "properties" -> Json.obj(
          "code" -> Json.obj(
            "type" -> Json.fromString("string"),
          ),
          "message" -> Json.obj(
            "type" -> Json.fromString("string"),
          ),
          "type" -> Json.obj(
            "type" -> Json.fromString("string"),
          ),
        )
      ),
      "Item" -> Json.obj(
        "type" -> Json.fromString("object"),
        "required" -> Json.arr(
          Json.fromString("name"),
          Json.fromString("price"),
          Json.fromString("category"),
          Json.fromString("createdAt"),
          Json.fromString("updatedAt"),
          Json.fromString("id"),
        ),
        "properties" -> Json.obj(
          "name" -> Json.obj(
            "type" -> Json.fromString("string"),
            "minLength" -> Json.fromInt(1),
          ),
          "price" -> Json.obj(
            "type" -> Json.fromString("number"),
            "format" -> Json.fromString("double"),
            "minimum" -> Json.fromDoubleOrNull(0.0),
          ),
          "category" -> Json.obj(
            "type" -> Json.fromString("string"),
            "minLength" -> Json.fromInt(1),
          ),
          "createdAt" -> Json.obj(
            "type" -> Json.fromString("string"),
            "format" -> Json.fromString("date-time"),
            "readOnly" -> Json.fromBoolean(true),
          ),
          "updatedAt" -> Json.obj(
            "type" -> Json.fromString("string"),
            "format" -> Json.fromString("date-time"),
            "readOnly" -> Json.fromBoolean(true),
          ),
          "id" -> Json.obj(
            "type" -> Json.fromString("integer"),
            "format" -> Json.fromString("int32"),
            "readOnly" -> Json.fromBoolean(true),
          ),
        ),
      ),
    )
  )

}
