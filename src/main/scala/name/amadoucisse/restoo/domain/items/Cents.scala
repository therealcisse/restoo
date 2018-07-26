package name.amadoucisse.restoo
package domain
package items

import io.circe._

final case class Cents private (value: Int) extends AnyVal

object Cents {
  implicit val encoder: Encoder[Cents] = Encoder.encodeInt.contramap[Cents](_.value)
  implicit val decoder: Decoder[Cents] = Decoder.decodeInt.map(Cents(_))

  def apply(value: Double): Cents = Cents((value * 100).toInt)
}
