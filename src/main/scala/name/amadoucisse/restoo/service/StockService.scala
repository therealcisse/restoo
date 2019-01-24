package name.amadoucisse.restoo
package service

import cats.NonEmptyParallel
import cats.implicits._
import cats.effect.{ Clock, Sync }
import domain.{ AppError, DateTime }
import domain.items.{ ItemId, ItemRepositoryAlgebra }
import domain.entries.{ Delta, Entry, EntryRepositoryAlgebra, Stock }

import java.util.concurrent.TimeUnit
import java.time.Instant

final class StockService[F[_]: Sync: Clock: Lambda[G[_] ⇒ NonEmptyParallel[G, G]]](
    entryRepo: EntryRepositoryAlgebra[F],
    itemRepo: ItemRepositoryAlgebra[F]
) {

  def createEntry(itemId: ItemId, delta: Delta): F[Stock] = {
    val getAction = getStock(itemId)

    val getTime = Clock[F].monotonic(TimeUnit.MILLISECONDS)

    val addAction = getTime >>= { now ⇒
      entryRepo.create(Entry(itemId, delta, DateTime(Instant.ofEpochMilli(now))))
    }

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
  def apply[F[_]: Sync: Clock: Lambda[G[_] ⇒ NonEmptyParallel[G, G]]](
      entryRepo: EntryRepositoryAlgebra[F],
      itemRepo: ItemRepositoryAlgebra[F]
  ): StockService[F] =
    new StockService[F](entryRepo, itemRepo)
}
