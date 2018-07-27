package name.amadoucisse.restoo
package domain
package entries

import io.circe._

final case class EntryId(value: Int) extends AnyVal

object EntryId {
  implicit val encoder: Encoder[EntryId] = Encoder.encodeInt.contramap[EntryId](_.value)
  implicit val decoder: Decoder[EntryId] = Decoder.decodeInt.map(EntryId(_))

}
