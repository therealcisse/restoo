package name.amadoucisse.restoo
package domain
package entries

import items.ItemId

@SuppressWarnings(Array("org.wartremover.warts.DefaultArguments"))
final case class Entry(
    itemId: ItemId,
    delta: Delta,
    timestamp: DateTime,
    id: Option[EntryId] = None,
)

object Entry {}
