package name.amadoucisse.restoo
package domain

import java.time.Instant

import io.circe._
import io.circe.generic.extras.semiauto._

final case class OccurredAt(value: Instant) extends AnyVal

object OccurredAt {
  implicit val encoder: Encoder[OccurredAt] = deriveUnwrappedEncoder
  implicit val decoder: Decoder[OccurredAt] = deriveUnwrappedDecoder

  def now: OccurredAt = OccurredAt(Instant.now)
}
