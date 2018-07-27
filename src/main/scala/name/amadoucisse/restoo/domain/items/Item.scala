package name.amadoucisse.restoo
package domain
package items

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}

final case class Item(
    name: Name,
    priceInCents: Cents,
    category: Category,
    createdAt: OccurredAt,
    updatedAt: OccurredAt,
    id: Option[ItemId] = None,
)

object Item {
  implicit def encoder: Encoder[Item] = deriveEncoder
  implicit def decoder: Decoder[Item] = deriveDecoder
}
