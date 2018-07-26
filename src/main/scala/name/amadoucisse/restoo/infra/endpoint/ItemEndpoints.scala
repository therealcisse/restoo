package name.amadoucisse.restoo
package infra
package endpoint

import scala.language.higherKinds

import cats.effect.Effect
import cats.implicits._

import io.circe.generic.auto._
import io.circe.syntax._

import org.http4s.circe._
import org.http4s.dsl.Http4sDsl
import org.http4s.{EntityDecoder, HttpService}

import domain._
import domain.items._

import service.ItemService

final class ItemEndpoints[F[_]: Effect, A, K] extends Http4sDsl[F] {
  import ItemEndpoints._

  implicit val createItemRequestDecoder: EntityDecoder[F, ItemRequest] = jsonOf

  private def createEndpoint(itemService: ItemService[F]): HttpService[F] =
    HttpService[F] {
      case req @ POST -> Root =>
        val action = for {
          itemRequest <- req.as[ItemRequest]
          item = itemRequest.asItem()
          result <- itemService.createItem(item).value
        } yield result

        action.flatMap {
          case Right(saved) => Ok(saved.asJson)
          case Left(ItemAlreadyExistsError(existing)) =>
            Conflict(s"The item with item name `$existing` already exists")
        }
    }

  private def updateEndpoint(itemService: ItemService[F]): HttpService[F] =
    HttpService[F] {
      case req @ PUT -> Root / LongVar(id) =>
        val action = for {
          itemRequest <- req.as[ItemRequest]
          item = itemRequest.asItem()
          updated = item.copy(id = ItemId(id).some)
          result <- itemService.update(updated).value
        } yield result

        action.flatMap {
          case Right(saved) => Ok(saved.asJson)
          case Left(ItemNotFoundError) => NotFound("Item not found")
        }
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
      case DELETE -> Root / LongVar(id) =>
        for {
          _ <- itemService.deleteItem(ItemId(id))
          resp <- Ok()
        } yield resp
    }

  private def getItemEndpoint(itemService: ItemService[F]): HttpService[F] =
    HttpService[F] {
      case GET -> Root / LongVar(id) =>
        val action = itemService.getItem(ItemId(id)).value

        action.flatMap {
          case Right(item) => Ok(item.asJson)
          case Left(ItemNotFoundError) => NotFound("Item not found")
        }
    }

  def endpoints(itemService: ItemService[F]): HttpService[F] =
    createEndpoint(itemService) <+>
      updateEndpoint(itemService) <+>
      deleteItemEndpoint(itemService) <+>
      getItemEndpoint(itemService) <+>
      listEndpoint(itemService)
}

object ItemEndpoints {
  def endpoints[F[_]: Effect, A, K](
      itemService: ItemService[F],
  ): HttpService[F] =
    new ItemEndpoints[F, A, K].endpoints(itemService)

  final case class ItemRequest(name: String, price: Double, category: String) {
    def asItem(): Item = Item(
      name = Name(name),
      priceInCents = Cents(price),
      category = Category(category),
      createdAt = OccurredAt.now.some,
      updatedAt = OccurredAt.now.some,
    )
  }

}
