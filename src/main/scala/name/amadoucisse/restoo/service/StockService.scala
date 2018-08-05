package name.amadoucisse.restoo
package service

import cats.Monad
import cats.syntax.flatMap._
import cats.syntax.functor._
import cats.syntax.applicative._
import cats.syntax.either._
import cats.data.EitherT
import domain.{AppError, ItemNotFound, ItemOutOfStock, OccurredAt}
import domain.items.{ItemId, ItemRepositoryAlgebra}
import domain.entries.{Delta, Entry, EntryRepositoryAlgebra, Stock}

final class StockService[F[_]: Monad](
    entryRepo: EntryRepositoryAlgebra[F],
    itemRepo: ItemRepositoryAlgebra[F]) {

  def createEntry(itemId: ItemId, delta: Delta): EitherT[F, AppError, Stock] = {
    val getAction: EitherT[F, AppError, Stock] = EitherT(getStock(itemId))

    val outOfStockErrorAction: EitherT[F, AppError, Stock] =
      EitherT.leftT[F, Stock](ItemOutOfStock)

    val addAction = EitherT
      .liftF(entryRepo.create(Entry(itemId, delta, OccurredAt.now)))

    getAction
      .flatMap { currentStock =>
        if (currentStock.quantity + delta.value < 0L) {
          outOfStockErrorAction
        } else {
          addAction.flatMap(_ => getAction)
        }
      }
  }

  def getStock(itemId: ItemId): F[AppError Either Stock] =
    itemRepo.get(itemId).flatMap {
      case Some(item) =>
        entryRepo
          .count(itemId)
          .map {
            case Some(quantity) => Stock(item, quantity).asRight
            case None => Stock(item, 0).asRight
          }

      case None => Either.left[AppError, Stock](ItemNotFound).pure[F]
    }

}

object StockService {
  def apply[F[_]: Monad](
      entryRepo: EntryRepositoryAlgebra[F],
      itemRepo: ItemRepositoryAlgebra[F]): StockService[F] =
    new StockService[F](entryRepo, itemRepo)
}
