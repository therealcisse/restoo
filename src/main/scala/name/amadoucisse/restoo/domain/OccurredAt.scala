package name.amadoucisse.restoo
package domain

import java.time.Instant

import io.circe._

final case class OccurredAt(value: Instant) extends AnyVal

object OccurredAt {
  implicit val encoder: Encoder[OccurredAt] = encodeInstant.contramap[OccurredAt](_.value)
  implicit val decoder: Decoder[OccurredAt] = decodeInstant.map(OccurredAt(_))

  def now = OccurredAt(Instant.now)
}
