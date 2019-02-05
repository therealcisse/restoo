package name.amadoucisse.restoo
package infra
package repository.inmemory

import cats.implicits._

import domain.entries._
import domain.items.ItemId

import cats.effect.concurrent.Ref
import cats.effect.Sync

final class EntryRepositoryInMemoryInterpreter[F[_]: Sync](ref: Ref[F, Map[EntryId, Entry]])
    extends EntryRepositoryAlgebra[F] {

  def create(entry: Entry): F[Unit] = ref.update(_.updated(entry.id, entry))

  def count(id: ItemId): F[Long] = ref.get.map { map ⇒
    val entries: List[Entry] = map.values.filter(_.itemId == id).toList

    entries.foldMap(_.delta.value.toLong)
  }
}

object EntryRepositoryInMemoryInterpreter {
  def apply[F[_]: Sync]: F[EntryRepositoryInMemoryInterpreter[F]] =
    Ref.of[F, Map[EntryId, Entry]](Map.empty).map(new EntryRepositoryInMemoryInterpreter[F](_))

}
