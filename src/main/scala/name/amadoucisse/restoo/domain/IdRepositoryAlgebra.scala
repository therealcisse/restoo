package name.amadoucisse.restoo
package domain

import items.ItemId
import entries.EntryId

trait IdRepositoryAlgebra[F[_]] {
  def newItemId: F[ItemId]

  def newEntryId: F[EntryId]
}
