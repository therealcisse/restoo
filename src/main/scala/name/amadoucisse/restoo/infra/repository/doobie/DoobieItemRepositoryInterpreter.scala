package name.amadoucisse.restoo
package infra
package repository.doobie

import cats.syntax.functor._
import cats.syntax.option._
import cats.effect.Sync
import doobie._
import doobie.implicits._
import doobie.postgres._
import domain.AppError
import domain.items._
import http.SortBy
import queries.ItemQueries

final class DoobieItemRepositoryInterpreter[F[_]: Sync](val xa: Transactor[F]) extends ItemRepositoryAlgebra[F] {

  def create(item: Item): F[Item] =
    ItemQueries
      .insert(item)
      .withUniqueGeneratedKeys[Int]("id")
      .map(id ⇒ item.copy(id = ItemId(id).some))
      .attemptSomeSqlState {
        case sqlstate.class23.UNIQUE_VIOLATION ⇒ AppError.itemAlreadyExists(item)
      }
      .flatMap {
        case Right(x) ⇒ FC.pure(x)
        case Left(e)  ⇒ FC.raiseError[Item](e)
      }
      .transact(xa)

  def update(item: Item): F[Item] =
    item.id match {
      case Some(id) ⇒
        ItemQueries
          .update(item, id)
          .run
          .attemptSomeSqlState {
            case sqlstate.class23.UNIQUE_VIOLATION ⇒ AppError.itemAlreadyExists(item)
          }
          .flatMap {
            case Right(affectedRows) ⇒
              if (affectedRows == 1) getItem(id)
              else FC.raiseError[Item](AppError.itemNotFound)

            case Left(e) ⇒ FC.raiseError[Item](e)
          }
          .transact(xa)

      case None ⇒ Sync[F].raiseError(AppError.itemNotFound)
    }

  private def getItem(id: ItemId): ConnectionIO[Item] =
    ItemQueries.select(id).option.flatMap {
      case Some(item) ⇒ FC.pure(item)
      case None       ⇒ FC.raiseError[Item](AppError.itemNotFound)
    }

  def get(id: ItemId): F[Item] =
    getItem(id).transact(xa)

  def findByName(name: Name): F[Item] =
    ItemQueries
      .byName(name)
      .option
      .flatMap {
        case Some(item) ⇒ FC.pure(item)
        case None       ⇒ FC.raiseError[Item](AppError.itemNotFound)
      }
      .transact(xa)

  def delete(itemId: ItemId): F[Unit] =
    ItemQueries.delete(itemId).run.void.transact(xa)

  def list(category: Option[Category], orderBy: Seq[SortBy]): fs2.Stream[F, Item] =
    ItemQueries.selectAll(category, orderBy).stream.transact(xa)
}

object DoobieItemRepositoryInterpreter {
  def apply[F[_]: Sync](xa: Transactor[F]): DoobieItemRepositoryInterpreter[F] =
    new DoobieItemRepositoryInterpreter(xa)
}
