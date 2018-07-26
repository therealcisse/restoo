package name.amadoucisse.restoo
package domain
package entries

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}

import items.Item

final case class Stock(
    item: Item,
    quantity: Int,
)

object Stock {
  implicit def encoder: Encoder[Stock] = deriveEncoder
  implicit def decoder: Decoder[Stock] = deriveDecoder

}
