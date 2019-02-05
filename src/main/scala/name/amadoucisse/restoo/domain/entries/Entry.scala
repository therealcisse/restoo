package name.amadoucisse.restoo
package domain
package entries

import items.ItemId

final case class Entry(
    itemId: ItemId,
    delta: Delta,
    timestamp: DateTime,
    id: EntryId,
)
