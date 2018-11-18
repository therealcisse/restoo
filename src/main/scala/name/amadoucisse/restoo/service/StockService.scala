package name.amadoucisse.restoo
package service

import cats.NonEmptyParallel
import cats.implicits._
import cats.effect.Sync
import domain.{ AppError, OccurredAt }
import domain.items.{ ItemId, ItemRepositoryAlgebra }
import domain.entries.{ Delta, Entry, EntryRepositoryAlgebra, Stock }

final class StockService[F[_]](entryRepo: EntryRepositoryAlgebra[F], itemRepo: ItemRepositoryAlgebra[F])(
    implicit F: Sync[F],
    P: NonEmptyParallel[F, F]
) {

  def createEntry(itemId: ItemId, delta: Delta): F[Stock] = {
    val getAction = getStock(itemId)

    val addAction = entryRepo.create(Entry(itemId, delta, OccurredAt.now))

    getAction
      .flatMap { stock ⇒
        if (stock.quantity + delta.value < 0L) {
          F.raiseError(AppError.itemOutOfStock)
        } else {
          addAction >> getAction
        }
      }
  }

  def getStock(itemId: ItemId): F[Stock] =
    (itemRepo.get(itemId), entryRepo.count(itemId)).parMapN(Stock.apply)

}

object StockService {
  def apply[F[_]: Sync](entryRepo: EntryRepositoryAlgebra[F],
                        itemRepo: ItemRepositoryAlgebra[F])(implicit P: NonEmptyParallel[F, F]): StockService[F] =
    new StockService[F](entryRepo, itemRepo)
}
