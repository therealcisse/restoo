package name.amadoucisse.restoo
package domain
package items
import io.circe.{ Decoder, Encoder }
import io.circe.generic.semiauto.{ deriveDecoder, deriveEncoder }

final case class Money(amount: Long, currency: CurrencyCode)

object Money {
  implicit val jsonEncoder: Encoder[Money] = deriveEncoder
  implicit val jsonDecoder: Decoder[Money] = deriveDecoder

}
