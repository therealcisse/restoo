package name.amadoucisse.restoo
package domain
package items

import io.circe._

final case class Category(value: String) extends AnyVal

object Category {
  implicit val encoder: Encoder[Category] = Encoder.encodeString.contramap[Category](_.value)
  implicit val decoder: Decoder[Category] = Decoder.decodeString.map(Category(_))

}
