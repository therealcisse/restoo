package name.amadoucisse.restoo
package domain
package items

import io.circe._
import io.circe.generic.extras.semiauto._

import eu.timepit.refined.auto._
import eu.timepit.refined.types.numeric.NonNegDouble

final case class Cents private[Cents] (value: Int) extends AnyVal {
  def toDouble: Double = value.toDouble / 100.0d
}

object Cents {
  implicit val jsonEncoder: Encoder[Cents] = deriveUnwrappedEncoder
  implicit val jsonDecoder: Decoder[Cents] = deriveUnwrappedDecoder

  def fromStandardAmount(amount: NonNegDouble): Cents = Cents((amount * 100.0d).toInt)
}
