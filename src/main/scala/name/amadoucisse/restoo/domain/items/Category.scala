package name.amadoucisse.restoo
package domain
package items

import io.circe._
import io.circe.generic.extras.semiauto._

final case class Category(value: String) extends AnyVal

object Category {
  implicit val encoder: Encoder[Category] = deriveUnwrappedEncoder
  implicit val decoder: Decoder[Category] = deriveUnwrappedDecoder

}
