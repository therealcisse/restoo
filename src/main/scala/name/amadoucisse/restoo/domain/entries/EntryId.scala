package name.amadoucisse.restoo
package domain
package entries

import io.circe._
import io.circe.generic.extras.semiauto._

final case class EntryId(value: Long) extends AnyVal

object EntryId {
  implicit val jsonEncoder: Encoder[EntryId] = deriveUnwrappedEncoder
  implicit val jsonDecoder: Decoder[EntryId] = deriveUnwrappedDecoder

}
