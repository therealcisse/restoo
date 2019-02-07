package name.amadoucisse.restoo
package infra
package repository.doobie

import cats.Monad
import doobie._
import doobie.implicits._
import domain.IdRepositoryAlgebra
import domain.items.ItemId
import domain.entries.EntryId
import queries.IdQueries

final class DoobieIdRepositoryInterpreter[F[_]: Monad](val xa: Transactor[F]) extends IdRepositoryAlgebra[F] {
  def newItemId: F[ItemId] =
    IdQueries.newItemId.unique
      .map(ItemId(_))
      .transact(xa)

  def newEntryId: F[EntryId] =
    IdQueries.newEntryId.unique
      .map(EntryId(_))
      .transact(xa)

}

object DoobieIdRepositoryInterpreter {
  def apply[F[_]: Monad](xa: Transactor[F]): DoobieIdRepositoryInterpreter[F] =
    new DoobieIdRepositoryInterpreter(xa)
}
