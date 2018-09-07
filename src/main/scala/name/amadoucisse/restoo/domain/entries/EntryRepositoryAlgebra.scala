package name.amadoucisse.restoo
package domain
package entries

import items.ItemId

trait EntryRepositoryAlgebra[F[_]] {
  def create(entry: Entry): F[Entry]

  def count(id: ItemId): F[Long]
}
