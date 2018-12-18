package name.amadoucisse.restoo
package infra
package repository.inmemory

import scala.util.Random

import cats.implicits._

import domain.entries._
import domain.items.ItemId

import cats.effect.concurrent.Ref
import cats.effect.Sync

final class EntryRepositoryInMemoryInterpreter[F[_]: Sync](state: Ref[F, Map[EntryId, Entry]])
    extends EntryRepositoryAlgebra[F] {

  def create(entry: Entry): F[Entry] = state.modify { map ⇒
    val id = EntryId(Random.nextInt.abs)
    val value = entry.copy(id = id.some)
    (map.updated(id, value), value)
  }

  def count(id: ItemId): F[Long] = state.get.map { map ⇒
    val itemEntries: Map[EntryId, Entry] = map.filter(_._2.itemId.value == id.value)

    itemEntries.foldLeft(0L)(_ + _._2.delta.value)
  }
}

object EntryRepositoryInMemoryInterpreter {
  def apply[F[_]: Sync]: F[EntryRepositoryInMemoryInterpreter[F]] =
    for {
      ref ← Ref.of[F, Map[EntryId, Entry]](Map.empty)
    } yield new EntryRepositoryInMemoryInterpreter[F](ref)

}
