package name.amadoucisse.restoo
package infra.repositoryimpl.inmemory

import cats.implicits._

import repository.IdRepository
import domain.entries._
import domain.items.ItemId

import cats.effect.concurrent.Ref
import cats.effect.Sync

final class IdRepositoryInMemoryInterpreter[F[_]: Sync](ref: Ref[F, Long]) extends IdRepository[F] {

  def newItemId: F[ItemId] = ref.modify(s ⇒ (s + 1L, ItemId(s)))

  def newEntryId: F[EntryId] = ref.modify(s ⇒ (s + 1L, EntryId(s)))
}

object IdRepositoryInMemoryInterpreter {
  def apply[F[_]: Sync]: F[IdRepositoryInMemoryInterpreter[F]] =
    Ref.of[F, Long](1L).map(new IdRepositoryInMemoryInterpreter[F](_))

}
