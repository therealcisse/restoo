package name.amadoucisse.restoo
package infra
package repository
package doobie

import cats.Monad
import cats.data.OptionT
import cats.implicits._

import _root_.doobie._
import _root_.doobie.implicits._

import domain.OccurredAt
import domain.items._

private object ItemSQL {

  def insert(item: Item): Update0 = sql"""
    INSERT INTO items (name, price_in_cents, category)
    VALUES (${item.name}, ${item.priceInCents}, ${item.category})
  """.update

  def update(item: Item, id: ItemId): Update0 = sql"""
    UPDATE items
    SET
      name = ${item.name},
      price_in_cents = ${item.priceInCents},
      category = ${item.category},
      updated_at = ${item.updatedAt}
    WHERE id = $id
  """.update

  def select(itemId: ItemId): Query0[Item] = sql"""
    SELECT
      name,
      price_in_cents,
      category,
      created_at,
      updated_at,
      id
    FROM items
    WHERE id = $itemId
  """.query

  def findByName(name: Name): Query0[Item] = sql"""
    SELECT
      name,
      price_in_cents,
      category,
      created_at,
      updated_at,
      id
    FROM items
    WHERE name = $name
  """.query

  def delete(itemId: ItemId): Update0 = sql"""
    DELETE FROM items WHERE id = $itemId
  """.update

  val selectAll: Query0[Item] = sql"""
    SELECT
      name,
      price_in_cents,
      category,
      created_at,
      updated_at,
      id
    FROM items
  """.query
}

final class DoobieItemRepositoryInterpreter[F[_]: Monad](val xa: Transactor[F])
    extends ItemRepositoryAlgebra[F] {

  def create(item: Item): F[Item] =
    ItemSQL
      .insert(item)
      .withUniqueGeneratedKeys[Long]("id")
      .map(id => item.copy(id = ItemId(id).some))
      .transact(xa)

  def update(item: Item): F[Option[Item]] =
    OptionT
      .fromOption[F](item.id)
      .flatMapF { id =>
        val newItem = item.copy(updatedAt = OccurredAt.now.some)
        ItemSQL
          .update(newItem, id)
          .run
          .transact(xa) *> get(id)
      }
      .value

  def get(id: ItemId): F[Option[Item]] = ItemSQL.select(id).option.transact(xa)

  def findByName(name: Name): F[Option[Item]] = ItemSQL.findByName(name).option.transact(xa)

  def delete(itemId: ItemId): F[Option[Item]] =
    OptionT(get(itemId)).semiflatMap(item => ItemSQL.delete(itemId).run.transact(xa).as(item)).value

  def list(): F[Vector[Item]] = ItemSQL.selectAll.to[Vector].transact(xa)
}

object DoobieItemRepositoryInterpreter {
  def apply[F[_]: Monad](xa: Transactor[F]): DoobieItemRepositoryInterpreter[F] =
    new DoobieItemRepositoryInterpreter(xa)
}
