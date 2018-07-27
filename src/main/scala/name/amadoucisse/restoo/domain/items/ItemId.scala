package name.amadoucisse.restoo
package domain
package items

import io.circe._

final case class ItemId(value: Int) extends AnyVal

object ItemId {
  implicit val encoder: Encoder[ItemId] = Encoder.encodeInt.contramap[ItemId](_.value)
  implicit val decoder: Decoder[ItemId] = Decoder.decodeInt.map(ItemId(_))

}
