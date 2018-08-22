package name.amadoucisse.restoo
package domain
package items

import io.circe._
import io.circe.generic.extras.semiauto._

final case class Category(value: String) extends AnyVal

object Category {
  implicit val jsonEncoder: Encoder[Category] = deriveUnwrappedEncoder
  implicit val jsonDecoder: Decoder[Category] = deriveUnwrappedDecoder

}
