package name.amadoucisse.restoo
package domain
package entries

import io.circe._
import io.circe.generic.extras.semiauto._

final case class Delta(value: Int) extends AnyVal

object Delta {
  implicit val jsonEncoder: Encoder[Delta] = deriveUnwrappedEncoder
  implicit val jsonDecoder: Decoder[Delta] = deriveUnwrappedDecoder

}
