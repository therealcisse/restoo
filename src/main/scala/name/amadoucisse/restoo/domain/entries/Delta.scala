package name.amadoucisse.restoo
package domain
package entries

import io.circe._

final case class Delta(value: Int) extends AnyVal

object Delta {
  implicit val encoder: Encoder[Delta] = Encoder.encodeInt.contramap[Delta](_.value)
  implicit val decoder: Decoder[Delta] = Decoder.decodeInt.map(Delta(_))

}
