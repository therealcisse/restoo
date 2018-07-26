package name.amadoucisse.restoo
package domain
package entries

import items.ItemId

final case class Entry(
    itemId: ItemId,
    delta: Delta,
    timestamp: OccurredAt,
    id: Option[EntryId] = None,
)

object Entry {}
