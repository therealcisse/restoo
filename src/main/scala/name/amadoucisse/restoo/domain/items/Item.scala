package name.amadoucisse.restoo
package domain
package items

final case class Item(
    name: Name,
    price: Money,
    category: Category,
    createdAt: DateTime,
    updatedAt: DateTime,
    id: ItemId,
)

object Item {
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
