package name.amadoucisse.restoo
package http

import cats.Monad
import io.circe.syntax._
import domain._
import domain.entries.Stock
import org.http4s.circe._
import org.http4s.dsl.Http4sDsl
import org.http4s.Response

final class HttpErrorHandler[F[_]: Monad] extends Http4sDsl[F] {

  val handle: AppError => F[Response[F]] = {
    case NoStockError(item) =>
      Ok(Stock(item, 0).asJson)

    case ErrorListing(errors) =>
      UnprocessableEntity(errors.asJson)

    case ItemNotFoundError =>
      NotFound("Item not found")

    case ItemAlreadyExistsError(existing) =>
      Conflict(s"The item with item name `${existing.name.value}` already exists")

    case ItemOutOfStockError =>
      Conflict("Item out of stock")
  }

}
