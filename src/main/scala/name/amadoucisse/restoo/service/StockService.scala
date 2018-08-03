package name.amadoucisse.restoo
package service

import cats.Monad
import cats.data.EitherT
import domain.{AppError, ItemNotFoundError, ItemOutOfStockError, NoStockError, OccurredAt}
import domain.items.{Item, ItemId, ItemRepositoryAlgebra}
import domain.entries.{Delta, Entry, EntryRepositoryAlgebra, Stock}

final class StockService[F[_]: Monad](
    entryRepo: EntryRepositoryAlgebra[F],
    itemRepo: ItemRepositoryAlgebra[F]) {

  def createEntry(itemId: ItemId, delta: Delta): EitherT[F, AppError, Stock] = {
    val getAction: EitherT[F, AppError, Stock] = EitherT(getStock(itemId))

    val outOfStockErrorAction: EitherT[F, AppError, Stock] =
      EitherT.leftT[F, Stock](ItemOutOfStockError)

    val noStockErrorAction: EitherT[F, AppError, Stock] =
      EitherT.leftT[F, Stock](ItemOutOfStockError)

    val addAction = EitherT
      .liftF(entryRepo.create(Entry(itemId, delta, OccurredAt.now)))
      .flatMap(_ => getAction)

    getAction
      .flatMap { currentStock =>
        if (currentStock.quantity + delta.value < 0L) {
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

  def getStock(itemId: ItemId): F[AppError Either Stock] =
    EitherT
      .fromOptionF[F, AppError, Item](itemRepo.get(itemId), ItemNotFoundError)
      .flatMap { item =>
        EitherT
          .fromOptionF[F, AppError, Long](entryRepo.count(itemId), NoStockError(item))
          .subflatMap(quantity => Right(Stock(item, quantity)))

      }
      .value

}

object StockService {
  def apply[F[_]: Monad](
      entryRepo: EntryRepositoryAlgebra[F],
      itemRepo: ItemRepositoryAlgebra[F]): StockService[F] =
    new StockService[F](entryRepo, itemRepo)
}
