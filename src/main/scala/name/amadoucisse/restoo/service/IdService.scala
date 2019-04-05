package name.amadoucisse.restoo
package service

import domain.items.ItemId
import domain.entries.EntryId
import repository.IdRepository

final class IdService[F[_]](idRepo: IdRepository[F]) {
  def newItemId: F[ItemId] = idRepo.newItemId

  def newEntryId: F[EntryId] =
    idRepo.newEntryId

}

object IdService {
  def apply[F[_]](repository: IdRepository[F]): IdService[F] =
    new IdService[F](repository)
}
