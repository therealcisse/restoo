package name.amadoucisse.restoo
package domain
package items

import io.circe._
import io.circe.generic.extras.semiauto._

final case class Name(value: String) extends AnyVal

object Name {
  implicit val jsonEncoder: Encoder[Name] = deriveUnwrappedEncoder
  implicit val jsonDecoder: Decoder[Name] = deriveUnwrappedDecoder

}
