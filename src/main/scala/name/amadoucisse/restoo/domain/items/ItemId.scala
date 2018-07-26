package name.amadoucisse.restoo
package domain
package items

import io.circe._

final case class ItemId(value: Long) extends AnyVal

object ItemId {
  implicit val encoder: Encoder[ItemId] = Encoder.encodeLong.contramap[ItemId](_.value)
  implicit val decoder: Decoder[ItemId] = Decoder.decodeLong.map(ItemId(_))

}
