package name.amadoucisse.restoo
package infra
package repository
package doobie

import cats.Monad
import cats.implicits._

import _root_.doobie._
import _root_.doobie.implicits._

import domain.items.ItemId
import domain.entries.{Entry, EntryId, EntryRepositoryAlgebra}

private object EntrySQL {

  def insert(entry: Entry): Update0 = sql"""
    INSERT INTO entries (item_id, delta)
    VALUES (${entry.itemId}, ${entry.delta})
  """.update

  def count(itemId: ItemId): Query0[Option[Int]] = sql"""
    SELECT
      SUM(delta) as quantity
    FROM entries
    WHERE item_id = $itemId
  """.query[Option[Int]]

}

final class DoobieEntryRepositoryInterpreter[F[_]: Monad](val xa: Transactor[F])
    extends EntryRepositoryAlgebra[F] {

  def create(entry: Entry): F[Entry] =
    EntrySQL
      .insert(entry)
      .withUniqueGeneratedKeys[Long]("id")
      .map(id => entry.copy(id = EntryId(id).some))
      .transact(xa)

  def count(id: ItemId): F[Option[Int]] = EntrySQL.count(id).unique.transact(xa)
}

object DoobieEntryRepositoryInterpreter {
  def apply[F[_]: Monad](xa: Transactor[F]): DoobieEntryRepositoryInterpreter[F] =
    new DoobieEntryRepositoryInterpreter(xa)
}
