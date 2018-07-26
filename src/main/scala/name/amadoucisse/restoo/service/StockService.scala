package name.amadoucisse.restoo
package service

import cats.Monad
import cats.data.EitherT

import domain.{ItemNotFoundError, ItemOutOfStockError, NoStockError, OccurredAt, ValidationError}
import domain.items.{ItemId, ItemRepositoryAlgebra}
import domain.entries.{Delta, Entry, EntryRepositoryAlgebra, Stock}

final class StockService[F[_]: Monad](
    entryRepo: EntryRepositoryAlgebra[F],
    itemRepo: ItemRepositoryAlgebra[F]) {

  def createEntry(itemId: ItemId, delta: Delta): EitherT[F, ValidationError, Stock] = {
    val getAction: EitherT[F, ValidationError, Stock] = getStock(itemId)

    val outOfStockErrorAction: EitherT[F, ValidationError, Stock] =
      EitherT.leftT[F, Stock](ItemOutOfStockError)

    val noStockErrorAction: EitherT[F, ValidationError, Stock] =
      EitherT.leftT[F, Stock](ItemOutOfStockError)

    val addAction = EitherT
      .liftF(entryRepo.create(Entry(itemId, delta, OccurredAt.now)))
      .flatMap(_ => getAction)

    getAction
      .flatMap { currentStock =>
        if (currentStock.quantity + delta.value < 0) {
          outOfStockErrorAction
        } else {
          addAction
        }
      }
      .recoverWith {
        case NoStockError(_) =>
          if (delta.value < 0) {
            noStockErrorAction
          } else {
            addAction
          }
      }
  }

  def getStock(itemId: ItemId): EitherT[F, ValidationError, Stock] =
    EitherT.fromOptionF(itemRepo.get(itemId), ItemNotFoundError).flatMap { item =>
      EitherT
        .fromOptionF(entryRepo.count(itemId), NoStockError(item))
        .subflatMap(quantity => Right(Stock(item, quantity)))

    }

}

object StockService {
  def apply[F[_]: Monad](
      entryRepo: EntryRepositoryAlgebra[F],
      itemRepo: ItemRepositoryAlgebra[F]): StockService[F] =
    new StockService[F](entryRepo, itemRepo)
}
