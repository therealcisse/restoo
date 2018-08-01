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
import name.amadoucisse.restoo.config.SwaggerConf
import service.{ItemService, StockService}

final class ItemEndpoints[F[_]: Effect] extends Http4sDsl[F] {
  import ItemEndpoints._

  implicit val createItemRequestDecoder: EntityDecoder[F, ItemRequest] = jsonOf

  implicit val stockRequestDecoder: EntityDecoder[F, StockRequest] = jsonOf

  private def createEndpoint(itemService: ItemService[F]): HttpService[F] =
    HttpService[F] {
      case req @ POST -> Root =>
        val action = for {
          itemRequest <- EitherT.right[AppError](req.as[ItemRequest])

          item <- EitherT.fromEither[F](itemRequest.validate)

          result <- EitherT(itemService.createItem(item).map(_.leftWiden[AppError]))
        } yield result

        action.value.flatMap {
          case Right(saved) => Ok(saved.asJson)
          case Left(ErrorListing(errors)) => UnprocessableEntity(errors.asJson)
          case Left(ItemAlreadyExistsError(existing)) =>
            Conflict(s"The item with item name `${existing.name.value}` already exists")
          case _ => BadRequest()
        }

    }

  private def updateEndpoint(itemService: ItemService[F]): HttpService[F] =
    HttpService[F] {
      case req @ (PUT | PATCH) -> Root / IntVar(id) =>
        val action = for {
          itemRequest <- EitherT.right[AppError](req.as[ItemRequest])

          item <- EitherT.fromEither[F](itemRequest.validate)

          updated = item.copy(id = ItemId(id).some)
          result <- EitherT(itemService.update(updated).map(_.leftWiden[AppError]))

        } yield result

        action.value.flatMap {
          case Right(saved) => Ok(saved.asJson)
          case Left(ErrorListing(errors)) => UnprocessableEntity(errors.asJson)
          case Left(ItemNotFoundError) => NotFound("Item not found")
          case _ => BadRequest()
        }

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
      case DELETE -> Root / IntVar(id) =>
        for {
          _ <- itemService.deleteItem(ItemId(id))
          resp <- Ok()
        } yield resp
    }

  private def getItemEndpoint(itemService: ItemService[F]): HttpService[F] =
    HttpService[F] {
      case GET -> Root / IntVar(id) =>
        val action = itemService.getItem(ItemId(id))

        action.flatMap {
          case Right(item) => Ok(item.asJson)
          case Left(ItemNotFoundError) => NotFound("Item not found")
        }
    }

  private def createStockEntryEndpoint(stockService: StockService[F]): HttpService[F] =
    HttpService[F] {
      case req @ (PUT | PATCH) -> Root / IntVar(itemId) / "stocks" =>
        val action = for {
          stockRequest <- req.as[StockRequest]
          result <- stockService.createEntry(ItemId(itemId), Delta(stockRequest.delta)).value
        } yield result

        action.flatMap {
          case Right(saved) => Ok(saved.asJson)
          case Left(ItemNotFoundError) => NotFound("Item not found")
          case Left(NoStockError(item)) => Ok(Stock(item, 0).asJson)
          case Left(ItemOutOfStockError) =>
            Conflict("Item out of stock")
          case _ => InternalServerError()
        }

    }

  private def getStockEndpoint(stockService: StockService[F]): HttpService[F] =
    HttpService[F] {
      case GET -> Root / IntVar(itemId) / "stocks" =>
        val action = stockService.getStock(ItemId(itemId))

        action.flatMap {
          case Right(stock) => Ok(stock.asJson)
          case Left(ItemNotFoundError) => NotFound("Item not found")
          case Left(NoStockError(item)) => Ok(Stock(item, 0).asJson)
          case _ => InternalServerError()
        }
    }

  private def getSwaggerDocs(swaggerConf: SwaggerConf): HttpService[F] =
    HttpService[F] {
      case GET -> Root / "swagger-spec.json" =>
        Ok(ItemEndpoints.swagger(swaggerConf))
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
      getSwaggerDocs(swaggerConf)
}

object ItemEndpoints {
  val ApiVersion = "v1"

  def endpoints[F[_]: Effect](
      itemService: ItemService[F],
      stockService: StockService[F],
      swaggerConf: SwaggerConf,
  ): HttpService[F] =
    new ItemEndpoints[F].endpoints(itemService, stockService, swaggerConf)

  final case class ItemRequest(name: String, price: Double, category: String) {
    import utils.Validator._

    def validate: Either[ErrorListing, Item] = {
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

  // TODO: Use Rho to do this once it's stable enough
  private def swagger(swaggerConf: SwaggerConf): Json = Json.obj(
    "swagger" -> Json.fromString("2.0"),
    "info" -> Json.obj(
      "description" -> Json.fromString("REST API for managing restaurant stock.")
    ),
    "host" -> Json.fromString(swaggerConf.host),
    "basePath" -> Json.fromString(s"/api/${ApiVersion}/items"),
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
          "responses" -> Json.obj("200" -> Json.obj(
            "description" -> Json.fromString("Success"),
            "schema" -> Json.obj(
              "$ref" -> Json.fromString("#/definitions/Item")
            ),
            "409" -> Json.obj(
              "description" -> Json.fromString("Item name already exists"),
            ),
            "422" -> Json.obj(
              "description" -> Json.fromString("Validation error"),
              "schema" -> Json.obj(
                "oneOf" -> Json.arr(
                  Json.obj(
                    "type" -> Json.fromString("array"),
                    "items" -> Json.obj(
                      "$ref" -> Json.fromString("#/definitions/FieldError")
                    )
                  ),
                  Json.obj(
                    "type" -> Json.fromString("string"),
                  )
                )
              ),
            )
          ))
        ),
      ),
      "/{ItemId}" -> Json.obj(
        "get" -> Json.obj(
          "summary" -> Json.fromString("Get single item"),
          "description" -> Json.fromString(""),
          "operationId" -> Json.fromString("getItem"),
          "consumes" -> Json.arr(),
          "produces" -> Json.arr(Json.fromString("application/json")),
          "parameters" -> Json.arr(Json.obj(
            "in" -> Json.fromString("path"),
            "name" -> Json.fromString("ItemId"),
            "description" -> Json.fromString("Id of the item."),
            "type" -> Json.fromString("integer"),
            "format" -> Json.fromString("int32"),
            "required" -> Json.fromBoolean(true),
          )),
          "responses" -> Json.obj("200" -> Json.obj(
            "description" -> Json.fromString("Success"),
            "schema" -> Json.obj(
              "$ref" -> Json.fromString("#/definitions/Item")
            ),
            "404" -> Json.obj(
              "description" -> Json.fromString("Not found"),
            ),
          ))
        ),
        "patch" -> Json.obj(
          "summary" -> Json.fromString("Update an item"),
          "description" -> Json.fromString(""),
          "operationId" -> Json.fromString("updateItem"),
          "consumes" -> Json.arr(Json.fromString("application/json")),
          "produces" -> Json.arr(Json.fromString("application/json")),
          "parameters" -> Json.arr(
            Json.obj(
              "in" -> Json.fromString("path"),
              "name" -> Json.fromString("ItemId"),
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
          "responses" -> Json.obj("200" -> Json.obj(
            "description" -> Json.fromString("Success"),
            "schema" -> Json.obj(
              "$ref" -> Json.fromString("#/definitions/Item")
            ),
            "422" -> Json.obj(
              "description" -> Json.fromString("Validation error"),
              "schema" -> Json.obj(
                "oneOf" -> Json.arr(
                  Json.obj(
                    "type" -> Json.fromString("array"),
                    "items" -> Json.obj(
                      "$ref" -> Json.fromString("#/definitions/FieldError")
                    )
                  ),
                  Json.obj(
                    "type" -> Json.fromString("string"),
                  )
                )
              ),
            )
          )),
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
              "name" -> Json.fromString("ItemId"),
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
          "responses" -> Json.obj("200" -> Json.obj(
            "description" -> Json.fromString("Success"),
            "schema" -> Json.obj(
              "$ref" -> Json.fromString("#/definitions/Item")
            ),
            "422" -> Json.obj(
              "description" -> Json.fromString("Validation error"),
              "schema" -> Json.obj(
                "oneOf" -> Json.arr(
                  Json.obj(
                    "type" -> Json.fromString("array"),
                    "items" -> Json.obj(
                      "$ref" -> Json.fromString("#/definitions/FieldError")
                    )
                  ),
                  Json.obj(
                    "type" -> Json.fromString("string"),
                  )
                )
              ),
            )
          )),
        ),
        "delete" -> Json.obj(
          "summary" -> Json.fromString("Delete an item"),
          "description" -> Json.fromString(""),
          "operationId" -> Json.fromString("deleteItem"),
          "consumes" -> Json.arr(),
          "produces" -> Json.arr(Json.fromString("application/json")),
          "parameters" -> Json.arr(Json.obj(
            "in" -> Json.fromString("path"),
            "name" -> Json.fromString("ItemId"),
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
      "/{ItemId}/stocks" -> Json.obj(
        "get" -> Json.obj(
          "summary" -> Json.fromString("Get item stock"),
          "description" -> Json.fromString(""),
          "operationId" -> Json.fromString("getItemStock"),
          "consumes" -> Json.arr(),
          "produces" -> Json.arr(Json.fromString("application/json")),
          "parameters" -> Json.arr(Json.obj(
            "in" -> Json.fromString("path"),
            "name" -> Json.fromString("ItemId"),
            "description" -> Json.fromString("Id of the item."),
            "type" -> Json.fromString("integer"),
            "required" -> Json.fromBoolean(true),
          )),
          "responses" -> Json.obj("200" -> Json.obj(
            "description" -> Json.fromString("Success"),
            "schema" -> Json.obj(
              "$ref" -> Json.fromString("#/definitions/Stock")
            ),
            "404" -> Json.obj(
              "description" -> Json.fromString("Not found"),
            ),
          ))
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
              "name" -> Json.fromString("ItemId"),
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
          "responses" -> Json.obj("200" -> Json.obj(
            "description" -> Json.fromString("Success"),
            "schema" -> Json.obj(
              "$ref" -> Json.fromString("#/definitions/Stock")
            ),
            "422" -> Json.obj(
              "description" -> Json.fromString("Invalid request entity"),
            ),
            "409" -> Json.obj(
              "description" -> Json.fromString("Item out of stock"),
            ),
          )),
        ),
        "patch" -> Json.obj(
          "summary" -> Json.fromString("Update item stock"),
          "description" -> Json.fromString(""),
          "operationId" -> Json.fromString("updateItemStock"),
          "consumes" -> Json.arr(Json.fromString("application/json")),
          "produces" -> Json.arr(Json.fromString("application/json")),
          "parameters" -> Json.arr(
            Json.obj(
              "in" -> Json.fromString("path"),
              "name" -> Json.fromString("ItemId"),
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
          "responses" -> Json.obj("200" -> Json.obj(
            "description" -> Json.fromString("Success"),
            "schema" -> Json.obj(
              "$ref" -> Json.fromString("#/definitions/Stock")
            ),
            "422" -> Json.obj(
              "description" -> Json.fromString("Invalid request entity"),
            ),
            "409" -> Json.obj(
              "description" -> Json.fromString("Item out of stock"),
            ),
          )),
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
      "FieldError" -> Json.obj(
        "type" -> Json.fromString("object"),
        "required" -> Json.arr(
          Json.fromString("name"),
          Json.fromString("message"),
        ),
        "properties" -> Json.obj(
          "name" -> Json.obj(
            "type" -> Json.fromString("string"),
          ),
          "message" -> Json.obj(
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
