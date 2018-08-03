package name.amadoucisse.restoo
package service

import cats._
import cats.implicits._
import cats.data.EitherT
import domain.items.{Item, ItemId, ItemRepositoryAlgebra, ItemValidationAlgebra}
import domain.{AppError, ItemAlreadyExistsError, ItemNotFoundError}

final class ItemService[F[_]: Monad](
    itemRepo: ItemRepositoryAlgebra[F],
    validation: ItemValidationAlgebra[F]) {

  def createItem(item: Item): F[AppError Either Item] =
    for {
      _ <- validation.doesNotExist(item)
      saved <- EitherT.liftF[F, ItemAlreadyExistsError, Item](itemRepo.create(item)).value
    } yield saved

  def getItem(itemId: ItemId): F[AppError Either Item] =
    itemRepo.get(itemId).map { item =>
      item.toRight[AppError](ItemNotFoundError)
    }

  def deleteItem(itemId: ItemId): F[Unit] = itemRepo.delete(itemId)

  def update(item: Item): F[AppError Either Item] =
    for {
      _ <- validation.exists(item.id)
      saved <- itemRepo.update(item)
    } yield saved.toRight[AppError](ItemNotFoundError)

  def list(): fs2.Stream[F, Item] =
    itemRepo.list()
}

object ItemService {
  def apply[F[_]: Monad](
      repository: ItemRepositoryAlgebra[F],
      validation: ItemValidationAlgebra[F]): ItemService[F] =
    new ItemService[F](repository, validation)
}
