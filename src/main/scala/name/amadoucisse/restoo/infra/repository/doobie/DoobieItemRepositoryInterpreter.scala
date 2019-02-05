package name.amadoucisse.restoo
package infra
package repository.doobie

import cats.syntax.functor._
import cats.effect.Sync
import doobie._
import doobie.implicits._
import doobie.postgres._
import domain.AppError
import domain.items._
import http.{ Page, SortBy }
import queries.ItemQueries

final class DoobieItemRepositoryInterpreter[F[_]: Sync](val xa: Transactor[F]) extends ItemRepositoryAlgebra[F] {

  def create(item: Item): F[Unit] =
    ItemQueries
      .insert(item)
      .run
      .attemptSomeSqlState {
        case sqlstate.class23.UNIQUE_VIOLATION ⇒ AppError.itemAlreadyExists(item)
      }
      .flatMap {
        case Left(e) ⇒ FC.raiseError[Unit](e)
        case _       ⇒ FC.pure(())
      }
      .transact(xa)

  def update(item: Item): F[Unit] =
    ItemQueries
      .update(item)
      .run
      .attemptSomeSqlState {
        case sqlstate.class23.UNIQUE_VIOLATION ⇒ AppError.itemAlreadyExists(item)
      }
      .flatMap {
        case Right(affectedRows) ⇒
          if (affectedRows == 1) FC.pure(())
          else FC.raiseError[Unit](AppError.itemNotFound)

        case Left(e) ⇒ FC.raiseError[Unit](e)
      }
      .transact(xa)

  def get(id: ItemId): F[Item] =
    ItemQueries
      .select(id)
      .option
      .flatMap {
        case Some(item) ⇒ FC.pure(item)
        case None       ⇒ FC.raiseError[Item](AppError.itemNotFound)
      }
      .transact(xa)

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

  def list(category: Option[Category], orderBy: Seq[SortBy], page: Page): fs2.Stream[F, Item] =
    ItemQueries.selectAll(category, orderBy, page).stream.transact(xa)
}

object DoobieItemRepositoryInterpreter {
  def apply[F[_]: Sync](xa: Transactor[F]): DoobieItemRepositoryInterpreter[F] =
    new DoobieItemRepositoryInterpreter(xa)
}
