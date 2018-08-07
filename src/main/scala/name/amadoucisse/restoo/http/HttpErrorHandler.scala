package name.amadoucisse.restoo
package http

import cats.Monad
import io.circe.Json
import io.circe.syntax._
import domain._
import org.http4s.circe._
import org.http4s.dsl.Http4sDsl
import org.http4s.Response

final class HttpErrorHandler[F[_]: Monad] extends Http4sDsl[F] {
  import AppError._

  val handle: AppError => F[Response[F]] = {
    case ErrorListing(errors) =>
      UnprocessableEntity(
        Json.obj(
          "code" -> Json.fromString(ApiResponseCodes.VALIDATION_FAILED),
          "type" -> Json.fromString("FieldErrors"),
          "message" -> Json.fromString("Validation failed"),
          "errors" -> errors.asJson.mapArray(
            _.map(_.mapObject(_.add("type", Json.fromString("FieldError")))))
        ))

    case ItemNotFound =>
      NotFound(ApiResponse("ItemNotFound", ApiResponseCodes.NOT_FOUND, "Item not found"))

    case ItemAlreadyExists(_) =>
      Conflict(ApiResponse("ItemAlreadyExists", ApiResponseCodes.CONFLICT, "Item already exists"))

    case ItemOutOfStock =>
      Conflict(ApiResponse("ItemOutOfStock", ApiResponseCodes.CONFLICT, "Item out of stock"))
  }

  private def ApiResponse(`type`: String, code: String, message: String) = Json.obj(
    "error" -> Json.obj(
      "type" -> Json.fromString(`type`),
      "code" -> Json.fromString(code),
      "message" -> Json.fromString(message)
    )
  )

}
