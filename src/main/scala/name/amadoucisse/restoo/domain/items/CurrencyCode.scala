package name.amadoucisse.restoo
package domain
package items
import io.circe.{ Decoder, Encoder }
import io.circe.generic.extras.semiauto.{ deriveUnwrappedDecoder, deriveUnwrappedEncoder }

final case class CurrencyCode(code: Int) extends AnyVal

object CurrencyCode {
  implicit val jsonEncoder: Encoder[CurrencyCode] = deriveUnwrappedEncoder
  implicit val jsonDecoder: Decoder[CurrencyCode] = deriveUnwrappedDecoder

}
