package name.amadoucisse.restoo
package infra
package endpoint

import scala.language.higherKinds
import cats.effect.Effect
import cats.data.Validated
import cats.implicits._
import io.circe.generic.auto._
import io.circe.syntax._
import org.http4s.circe._
import org.http4s.dsl.Http4sDsl
import org.http4s.{EntityDecoder, HttpService}
import domain._
import domain.items._
import service.ItemService

final class ItemEndpoints[F[_]: Effect] extends Http4sDsl[F] {
  import ItemEndpoints._

  implicit val createItemRequestDecoder: EntityDecoder[F, ItemRequest] = jsonOf

  private def createEndpoint(itemService: ItemService[F]): HttpService[F] =
    HttpService[F] {
      case req @ POST -> Root =>
        for {
          itemRequest <- req.as[ItemRequest]

          response <- itemRequest.toValidatedItem match {
            case Validated.Invalid(errors) => UnprocessableEntity(errors.asJson)
            case Validated.Valid(item) =>
              val action = itemService.createItem(item).value

              action.flatMap {
                case Right(saved) => Ok(saved.asJson)
                case Left(ItemAlreadyExistsError(existing)) =>
                  Conflict(s"The item with item name `${existing.name.value}` already exists")
              }

          }

        } yield response

    }

  private def updateEndpoint(itemService: ItemService[F]): HttpService[F] =
    HttpService[F] {
      case req @ PUT -> Root / IntVar(id) =>
        for {
          itemRequest <- req.as[ItemRequest]

          response <- itemRequest.toValidatedItem match {
            case Validated.Invalid(errors) => UnprocessableEntity(errors.asJson)
            case Validated.Valid(item) =>
              val updated = item.copy(id = ItemId(id).some)
              val action = itemService.update(updated).value

              action.flatMap {
                case Right(saved) => Ok(saved.asJson)
                case Left(ItemNotFoundError) => NotFound("Item not found")
              }
          }

        } yield response

    }

  private def listEndpoint(itemService: ItemService[F]): HttpService[F] =
    HttpService[F] {
      case GET -> Root =>
        for {
          retrieved <- itemService.list()
          resp <- Ok(retrieved.asJson)
        } yield resp
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
        val action = itemService.getItem(ItemId(id)).value

        action.flatMap {
          case Right(item) => Ok(item.asJson)
          case Left(ItemNotFoundError) => NotFound("Item not found")
        }
    }

  @SuppressWarnings(Array("org.wartremover.warts.Any"))
  def endpoints(itemService: ItemService[F]): HttpService[F] =
    createEndpoint(itemService) <+>
      updateEndpoint(itemService) <+>
      deleteItemEndpoint(itemService) <+>
      getItemEndpoint(itemService) <+>
      listEndpoint(itemService)
}

object ItemEndpoints {
  def endpoints[F[_]: Effect](
      itemService: ItemService[F],
  ): HttpService[F] =
    new ItemEndpoints[F].endpoints(itemService)

  final case class ItemRequest(name: String, price: Double, category: String) {
    import utils.Validator._

    def toValidatedItem: ValidationResult[Item] =
      (
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
  }

}
