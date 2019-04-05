package name.amadoucisse.restoo
package repository

import domain.items.ItemId
import domain.entries.EntryId

trait IdRepository[F[_]] {
  def newItemId: F[ItemId]

  def newEntryId: F[EntryId]
}
