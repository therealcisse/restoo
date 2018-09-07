package name.amadoucisse.restoo
package service

import cats.syntax.flatMap._
import cats.syntax.functor._
import cats.effect.Sync
import domain.{ AppError, OccurredAt }
import domain.items.{ ItemId, ItemRepositoryAlgebra }
import domain.entries.{ Delta, Entry, EntryRepositoryAlgebra, Stock }

final class StockService[F[_]](entryRepo: EntryRepositoryAlgebra[F], itemRepo: ItemRepositoryAlgebra[F])(
    implicit F: Sync[F]
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
    for {
      item ← itemRepo.get(itemId)
      quantity ← entryRepo.count(itemId)
    } yield Stock(item, quantity)

}

object StockService {
  def apply[F[_]: Sync](entryRepo: EntryRepositoryAlgebra[F], itemRepo: ItemRepositoryAlgebra[F]): StockService[F] =
    new StockService[F](entryRepo, itemRepo)
}
