package name.amadoucisse.restoo

import io.circe.Json

package object http {

  case object ApiResponseCodes {

    val VALIDATION_FAILED = "VALIDATION_FAILED"

    val NOT_FOUND = "NOT_FOUND"

    val CONFLICT = "CONFLICT"
  }

  private[http] def ApiResponse(`type`: String, code: String, message: String) = Json.obj(
    "error" → Json.obj(
      "type" → Json.fromString(`type`),
      "code" → Json.fromString(code),
      "message" → Json.fromString(message)
    )
  )

}
