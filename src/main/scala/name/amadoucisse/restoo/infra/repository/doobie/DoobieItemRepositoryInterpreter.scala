package name.amadoucisse.restoo
package infra
package repository.doobie

import cats._
import cats.data.EitherT
import cats.implicits._
import doobie._
import doobie.implicits._
import doobie.postgres._
import domain.{AppError, OccurredAt}
import domain.items._
import http.{OrderBy, SortBy}

private object ItemSQL extends SQLCommon {

  def insert(item: Item): Update0 = sql"""
    INSERT INTO items (name, price_in_cents, category, created_at, updated_at)
    VALUES (${item.name}, ${item.priceInCents}, ${item.category}, ${item.createdAt}, ${item.updatedAt})
  """.update

  def touch(id: ItemId): Update0 = sql"""
    UPDATE items
    SET
      updated_at = ${OccurredAt.now}
    WHERE id = $id
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

  def selectAll(category: Option[Category], sort: Seq[SortBy]): Query0[Item] = {

    val baseSQL = sql"""
    SELECT
      name,
      price_in_cents,
      category,
      created_at,
      updated_at,
      id
    FROM items
    """

    val conds = Fragments.whereAndOpt(category.map(c => fr"category = $c"))

    def getOrderBySQL(orderBy: SortBy) = orderBy match {
      case SortBy(name, OrderBy.Ascending) => Fragment.const(s"$name ASC")
      case SortBy(name, OrderBy.Descending) => Fragment.const(s"$name DESC")
    }

    val orderBy =
      if (sort.isEmpty) Fragment.empty
      else fr"ORDER BY" ++ sort.toList.map(getOrderBySQL).intercalate(fr",")

    (baseSQL ++ conds ++ orderBy).query
  }
}

final class DoobieItemRepositoryInterpreter[F[_]: Monad](val xa: Transactor[F])
    extends ItemRepositoryAlgebra[F] {

  @SuppressWarnings(Array("org.wartremover.warts.Any"))
  def create(item: Item): F[AppError Either Item] =
    ItemSQL
      .insert(item)
      .withUniqueGeneratedKeys[Int]("id")
      .map(id => item.copy(id = ItemId(id).some))
      .attemptSomeSqlState {
        case sqlstate.class23.UNIQUE_VIOLATION => AppError.itemAlreadyExists(item)
      }
      .transact(xa)

  def update(item: Item): F[AppError Either Item] =
    EitherT
      .fromOption[F](item.id, AppError.itemNotFound)
      .flatMapF { id =>
        val newItem = item.copy(updatedAt = OccurredAt.now)
        ItemSQL
          .update(newItem, id)
          .run
          .attemptSomeSqlState {
            case sqlstate.class23.UNIQUE_VIOLATION => AppError.duplicateItem(item.name)
          }
          .transact(xa)
          .flatMap {
            case Right(affectedRows) =>
              if (affectedRows == 1) get(id).map(_.toRight(AppError.itemNotFound))
              else AppError.itemNotFound.asLeft[Item].pure[F]

            case Left(e) => e.asLeft[Item].pure[F]
          }

      }
      .value

  def get(id: ItemId): F[Option[Item]] =
    ItemSQL.select(id).option.transact(xa)

  def findByName(name: Name): F[Option[Item]] =
    ItemSQL.byName(name).option.transact(xa)

  def delete(itemId: ItemId): F[Unit] =
    ItemSQL.delete(itemId).run.transact(xa).void

  def list(category: Option[Category], orderBy: Seq[SortBy]): fs2.Stream[F, Item] =
    ItemSQL.selectAll(category, orderBy).stream.transact(xa)
}

object DoobieItemRepositoryInterpreter {
  def apply[F[_]: Monad](xa: Transactor[F]): DoobieItemRepositoryInterpreter[F] =
    new DoobieItemRepositoryInterpreter(xa)
}
