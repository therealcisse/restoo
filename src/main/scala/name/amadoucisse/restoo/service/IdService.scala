package name.amadoucisse.restoo
package service

import domain.items.ItemId
import domain.entries.EntryId
import domain.IdRepositoryAlgebra

final class IdService[F[_]](idRepo: IdRepositoryAlgebra[F]) {
  def newItemId: F[ItemId] = idRepo.newItemId

  def newEntryId: F[EntryId] =
    idRepo.newEntryId

}

object IdService {
  def apply[F[_]](repository: IdRepositoryAlgebra[F]): IdService[F] =
    new IdService[F](repository)
}
