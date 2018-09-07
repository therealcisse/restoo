package name.amadoucisse.restoo
package utils

import io.circe.{ Decoder, Encoder }
import io.circe.generic.semiauto.{ deriveDecoder, deriveEncoder }

object Validation {
  final case class FieldError(id: String, message: String)

  object FieldError {
    implicit def jsonEncoder: Encoder[FieldError] = deriveEncoder
    implicit def jsonDecoder: Decoder[FieldError] = deriveDecoder
  }

}
