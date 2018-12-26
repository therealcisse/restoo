package name.amadoucisse.restoo
package domain

import java.time.Instant

import io.circe._
import io.circe.generic.extras.semiauto._

final case class DateTime(value: Instant) extends AnyVal

object DateTime {
  implicit val jsonEncoder: Encoder[DateTime] = deriveUnwrappedEncoder
  implicit val jsonDecoder: Decoder[DateTime] = deriveUnwrappedDecoder

}
