package name.amadoucisse.restoo
package domain
package entries

import items.Item

final case class Stock(
    item: Item,
    quantity: Long,
)
