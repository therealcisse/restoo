package name.amadoucisse.restoo
package service

import cats._
import cats.implicits._
import domain.items.{Item, ItemId, ItemRepositoryAlgebra, ItemValidationAlgebra}
import domain.{AppError, ItemNotFound}

final class ItemService[F[_]: Monad](
    itemRepo: ItemRepositoryAlgebra[F],
    validation: ItemValidationAlgebra[F]) {

  def createItem(item: Item): F[AppError Either Item] =
    itemRepo.create(item)

  def getItem(itemId: ItemId): F[AppError Either Item] =
    itemRepo.get(itemId).map(_.toRight[AppError](ItemNotFound))

  def deleteItem(itemId: ItemId): F[Unit] = itemRepo.delete(itemId)

  def update(item: Item): F[AppError Either Item] =
    for {
      itemExists <- validation.exists(item.id)
      saved <- if (itemExists) itemRepo.update(item).map(_.toRight[AppError](ItemNotFound))
      else Either.left[AppError, Item](ItemNotFound).pure[F]
    } yield saved

  def list(): fs2.Stream[F, Item] =
    itemRepo.list()
}

object ItemService {
  def apply[F[_]: Monad](
      repository: ItemRepositoryAlgebra[F],
      validation: ItemValidationAlgebra[F]): ItemService[F] =
    new ItemService[F](repository, validation)
}
