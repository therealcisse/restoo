package name.amadoucisse.restoo
package infra
package repository.doobie

import cats.Monad
import cats.data.EitherT
import cats.implicits._

import doobie._
import doobie.implicits._

import domain.OccurredAt
import domain.items._
import domain.ItemNotFoundError

private object ItemSQL extends SQLCommon {

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

  def byName(name: Name): Query0[Item] = sql"""
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
      .withUniqueGeneratedKeys[Int]("id")
      .map(id => item.copy(id = ItemId(id).some))
      .transact(xa)

  def update(item: Item): F[ItemNotFoundError.type Either Item] =
    EitherT
      .fromOption[F](item.id, ItemNotFoundError)
      .flatMapF { id =>
        val newItem = item.copy(updatedAt = OccurredAt.now)
        ItemSQL
          .update(newItem, id)
          .run
          .transact(xa) *> get(id)
      }
      .value

  def get(id: ItemId): F[ItemNotFoundError.type Either Item] =
    EitherT.fromOptionF(ItemSQL.select(id).option.transact(xa), ItemNotFoundError).value

  def findByName(name: Name): F[ItemNotFoundError.type Either Item] =
    EitherT.fromOptionF(ItemSQL.byName(name).option.transact(xa), ItemNotFoundError).value

  def delete(itemId: ItemId): F[ItemNotFoundError.type Either Unit] =
    ItemSQL.delete(itemId).run.transact(xa).map { affectedRows =>
      if (affectedRows == 1) Right(())
      else Left(ItemNotFoundError)
    }

  def list(): fs2.Stream[F, Item] = ItemSQL.selectAll.stream.transact(xa)
}

object DoobieItemRepositoryInterpreter {
  def apply[F[_]: Monad](xa: Transactor[F]): DoobieItemRepositoryInterpreter[F] =
    new DoobieItemRepositoryInterpreter(xa)
}
