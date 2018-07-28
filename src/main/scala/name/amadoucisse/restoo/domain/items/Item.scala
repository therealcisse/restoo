package name.amadoucisse.restoo
package domain
package items

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}

@SuppressWarnings(Array("org.wartremover.warts.DefaultArguments"))
final case class Item(
    name: Name,
    priceInCents: Cents,
    category: Category,
    createdAt: OccurredAt = OccurredAt.now,
    updatedAt: OccurredAt = OccurredAt.now,
    id: Option[ItemId] = None,
)

object Item {
  implicit def encoder: Encoder[Item] = deriveEncoder
  implicit def decoder: Decoder[Item] = deriveDecoder
}
