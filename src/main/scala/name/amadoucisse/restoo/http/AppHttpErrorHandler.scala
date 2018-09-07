package name.amadoucisse.restoo
package http

import cats.MonadError
import domain.AppError
import org.http4s.{ HttpRoutes, Response }
import org.http4s.dsl.Http4sDsl
import io.circe.Json
import io.circe.syntax._
import org.http4s.circe._

final class AppHttpErrorHandler[F[_]](implicit M: MonadError[F, AppError])
    extends HttpErrorHandler[F, AppError]
    with Http4sDsl[F] {
  import AppError._

  private val handler: AppError ⇒ F[Response[F]] = {
    case s @ ValidationFailed(errors) ⇒
      UnprocessableEntity(
        Json.obj(
          "code" → Json.fromString(ApiResponseCodes.VALIDATION_FAILED),
          "type" → Json.fromString("FieldErrors"),
          "message" → Json.fromString(s.message),
          "errors" → errors.asJson.mapArray(_.map(_.mapObject(_.add("type", Json.fromString("FieldError")))))
        )
      )

    case s @ ItemNotFound ⇒
      NotFound(ApiResponse("ItemNotFound", ApiResponseCodes.NOT_FOUND, s.message))

    case s @ ItemAlreadyExists(_) ⇒
      Conflict(ApiResponse("ItemAlreadyExists", ApiResponseCodes.CONFLICT, s.message))

    case s @ ItemOutOfStock ⇒
      Conflict(ApiResponse("ItemOutOfStock", ApiResponseCodes.CONFLICT, s.message))

    case s @ InvalidJsonPatch ⇒
      Conflict(ApiResponse("InvalidJsonPatch", ApiResponseCodes.VALIDATION_FAILED, s.message))
  }

  override def handle(service: HttpRoutes[F]): HttpRoutes[F] =
    RoutesHttpErrorHandler(service)(handler)

}
