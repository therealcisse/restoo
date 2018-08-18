package name.amadoucisse.restoo
package domain
package items

import io.circe._
import io.circe.generic.extras.semiauto._

final case class Cents private[Cents] (value: Int) extends AnyVal {
  def toDouble = value.toDouble / 100.0d
}

object Cents {
  implicit val encoder: Encoder[Cents] = deriveUnwrappedEncoder
  implicit val decoder: Decoder[Cents] = deriveUnwrappedDecoder

  def fromStandardAmount(amount: Double): Cents = Cents((amount * 100.0d).toInt)
}
