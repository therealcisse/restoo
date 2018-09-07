package name.amadoucisse.restoo

import java.time.Instant

import io.circe._
import java.time.format.DateTimeFormatter

package object domain {

  private val dateFormat = DateTimeFormatter.ISO_INSTANT

  implicit val encodeInstant: Encoder[Instant] =
    Encoder.encodeString.contramap[Instant](dateFormat.format(_))
  implicit val decodeInstant: Decoder[Instant] =
    Decoder.decodeString.map(str ⇒ dateFormat.parse(str, Instant.from(_)))
}
