package name.amadoucisse.restoo
package repository

import domain.items.ItemId
import domain.entries.Entry

trait EntryRepository[F[_]] {
  def create(entry: Entry): F[Unit]

  def count(id: ItemId): F[Long]
}
