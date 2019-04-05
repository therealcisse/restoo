package name.amadoucisse.restoo
package service

import cats.temp.par._
import cats.implicits._
import cats.effect.{ Clock, Sync }
import domain.{ AppError, DateTime }
import repository.{ EntryRepository, IdRepository, ItemRepository }
import domain.items.ItemId
import domain.entries.{ Delta, Entry, Stock }

import java.util.concurrent.TimeUnit
import java.time.Instant

final class StockService[F[_]: Sync: Clock: Par](
    entryRepo: EntryRepository[F],
    itemRepo: ItemRepository[F],
    idRepo: IdRepository[F],
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
  def apply[F[_]: Sync: Clock: Par](
      entryRepo: EntryRepository[F],
      itemRepo: ItemRepository[F],
      idRepo: IdRepository[F],
  ): StockService[F] =
    new StockService[F](entryRepo, itemRepo, idRepo)
}
