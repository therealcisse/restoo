package name.amadoucisse.restoo
package domain
package items

import io.circe._

import scala.util.Try

final case class ItemId(value: Int) extends AnyVal

object ItemId {
  implicit val encoder: Encoder[ItemId] = Encoder.encodeInt.contramap[ItemId](_.value)
  implicit val decoder: Decoder[ItemId] = Decoder.decodeInt.map(ItemId(_))

  def unapply(param: String): Option[ItemId] =
    if (param.isEmpty) None
    else Try(ItemId(param.toInt)).toOption
}
