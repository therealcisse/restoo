package name.amadoucisse.restoo
package domain
package entries

import io.circe._

final case class EntryId(value: Long) extends AnyVal

object EntryId {
  implicit val encoder: Encoder[EntryId] = Encoder.encodeLong.contramap[EntryId](_.value)
  implicit val decoder: Decoder[EntryId] = Decoder.decodeLong.map(EntryId(_))

}
