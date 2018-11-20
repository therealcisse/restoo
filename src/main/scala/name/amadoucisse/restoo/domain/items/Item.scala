package name.amadoucisse.restoo
package domain
package items

import io.circe.{ Decoder, Encoder }
import io.circe.generic.semiauto.{ deriveDecoder, deriveEncoder }

@SuppressWarnings(Array("org.wartremover.warts.DefaultArguments"))
final case class Item(
    name: Name,
    price: Money,
    category: Category,
    createdAt: DateTime = DateTime.now,
    updatedAt: DateTime = DateTime.now,
    id: Option[ItemId] = None,
)

object Item {
  implicit def jsonEncoder: Encoder[Item] = deriveEncoder
  implicit def jsonDecoder: Decoder[Item] = deriveDecoder

  import eu.timepit.refined.W
  import eu.timepit.refined.generic.Equal
  import eu.timepit.refined.boolean.Or

  type SortableField =
    Equal[W.`"name"`.T] Or
      Equal[W.`"price"`.T] Or
      Equal[W.`"category"`.T] Or
      Equal[W.`"created_at"`.T] Or
      Equal[W.`"updated_at"`.T]

  type PatchableField =
    Equal[W.`"/name"`.T] Or
      Equal[W.`"/priceInCents"`.T] Or
      Equal[W.`"/category"`.T]
}
