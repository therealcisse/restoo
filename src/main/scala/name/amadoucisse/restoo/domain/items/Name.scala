package name.amadoucisse.restoo
package domain
package items

import io.circe._
import io.circe.generic.extras.semiauto._

final case class Name(value: String) extends AnyVal

object Name {
  implicit val encoder: Encoder[Name] = deriveUnwrappedEncoder
  implicit val decoder: Decoder[Name] = deriveUnwrappedDecoder

}
