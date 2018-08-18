package name.amadoucisse.restoo
package domain
package entries

import io.circe._
import io.circe.generic.extras.semiauto._

final case class EntryId(value: Int) extends AnyVal

object EntryId {
  implicit val encoder: Encoder[EntryId] = deriveUnwrappedEncoder
  implicit val decoder: Decoder[EntryId] = deriveUnwrappedDecoder

}
