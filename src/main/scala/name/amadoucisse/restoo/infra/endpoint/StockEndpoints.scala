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
import domain.entries._
import domain.items._

import service.StockService

final class StockEndpoints[F[_]: Effect, A, K] extends Http4sDsl[F] {
  import StockEndpoints._

  implicit val stockRequestDecoder: EntityDecoder[F, StockRequest] = jsonOf

  private def createEndpoint(stockService: StockService[F]): HttpService[F] =
    HttpService[F] {
      case req @ PUT -> Root / LongVar(itemId) / "stocks" =>
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
      case GET -> Root / LongVar(itemId) / "stocks" =>
        val action = stockService.getStock(ItemId(itemId)).value

        action.flatMap {
          case Right(stock) => Ok(stock.asJson)
          case Left(ItemNotFoundError) => NotFound("Item not found")
          case Left(NoStockError(item)) => Ok(Stock(item, 0).asJson)
          case _ => InternalServerError()
        }
    }

  def endpoints(stockService: StockService[F]): HttpService[F] =
    createEndpoint(stockService) <+>
      getStockEndpoint(stockService)
}

object StockEndpoints {
  def endpoints[F[_]: Effect, A, K](
      stockService: StockService[F],
  ): HttpService[F] =
    new StockEndpoints[F, A, K].endpoints(stockService)

  final case class StockRequest(delta: Int) extends AnyVal
}
