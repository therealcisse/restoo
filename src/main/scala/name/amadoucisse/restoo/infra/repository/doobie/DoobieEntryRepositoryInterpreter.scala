package name.amadoucisse.restoo
package infra
package repository.doobie

import cats._
import cats.implicits._

import doobie._
import doobie.implicits._

import domain.items.ItemId
import domain.entries.{ Entry, EntryRepositoryAlgebra }

import queries.{ EntryQueries, ItemQueries }

final class DoobieEntryRepositoryInterpreter[F[_]: Monad](val xa: Transactor[F]) extends EntryRepositoryAlgebra[F] {

  def create(entry: Entry): F[Unit] = {
    val addEntryAction = EntryQueries
      .insert(entry)
      .run

    val program = addEntryAction <* ItemQueries.touch(entry.itemId, entry.timestamp).run

    program.transact(xa).void
  }

  def count(id: ItemId): F[Long] =
    EntryQueries
      .count(id)
      .unique
      .flatMap {
        case Some(len) ⇒ FC.pure(len)
        case None      ⇒ FC.pure(0L)
      }
      .transact(xa)
}

object DoobieEntryRepositoryInterpreter {
  def apply[F[_]: Monad](xa: Transactor[F]): DoobieEntryRepositoryInterpreter[F] =
    new DoobieEntryRepositoryInterpreter(xa)
}
