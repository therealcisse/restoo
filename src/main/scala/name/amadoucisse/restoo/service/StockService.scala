package name.amadoucisse.restoo
package service

import cats.NonEmptyParallel
import cats.implicits._
import cats.effect.{ Clock, Sync }
import domain.{ AppError, DateTime }
import domain.IdRepositoryAlgebra
import domain.items.{ ItemId, ItemRepositoryAlgebra }
import domain.entries.{ Delta, Entry, EntryRepositoryAlgebra, Stock }

import java.util.concurrent.TimeUnit
import java.time.Instant

final class StockService[F[_]: Sync: Clock: λ[G[_] ⇒ NonEmptyParallel[G, G]]](
    entryRepo: EntryRepositoryAlgebra[F],
    itemRepo: ItemRepositoryAlgebra[F],
    idRepo: IdRepositoryAlgebra[F],
) {

  def createEntry(itemId: ItemId, delta: Delta): F[Stock] = {
    val getAction = getStock(itemId)

    val getTimeAction = Clock[F].monotonic(TimeUnit.MILLISECONDS)

    val addAction = (getTimeAction, idRepo.newEntryId).parMapN { (now, newId) ⇒
      Entry(
        itemId,
        delta,
        DateTime(Instant.ofEpochMilli(now)),
        id = newId
      )
    } >>= entryRepo.create

    getAction
      .flatMap { stock ⇒
        if (stock.quantity + delta.value < 0L) {
          Sync[F].raiseError(AppError.itemOutOfStock)
        } else {
          addAction >> getAction
        }
      }
  }

  def getStock(itemId: ItemId): F[Stock] =
    (itemRepo.get(itemId), entryRepo.count(itemId)).parMapN(Stock(_, _))

}

object StockService {
  def apply[F[_]: Sync: Clock: λ[G[_] ⇒ NonEmptyParallel[G, G]]](
      entryRepo: EntryRepositoryAlgebra[F],
      itemRepo: ItemRepositoryAlgebra[F],
      idRepo: IdRepositoryAlgebra[F],
  ): StockService[F] =
    new StockService[F](entryRepo, itemRepo, idRepo)
}
