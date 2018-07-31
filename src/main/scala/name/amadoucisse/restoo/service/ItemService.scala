package name.amadoucisse.restoo
package service

import cats._
import cats.implicits._
import cats.data.EitherT
import domain.items.{Item, ItemId, ItemRepositoryAlgebra, ItemValidationAlgebra}
import domain.{ItemAlreadyExistsError, ItemNotFoundError}

final class ItemService[F[_]: Monad](
    itemRepo: ItemRepositoryAlgebra[F],
    validation: ItemValidationAlgebra[F]) {

  def createItem(item: Item): EitherT[F, ItemAlreadyExistsError, Item] =
    for {
      _ <- validation.doesNotExist(item)
      saved <- EitherT.liftF(itemRepo.create(item))
    } yield saved

  def getItem(itemId: ItemId): EitherT[F, ItemNotFoundError.type, Item] =
    EitherT.fromOptionF(itemRepo.get(itemId), ItemNotFoundError)

  def deleteItem(itemId: ItemId): F[Unit] = itemRepo.delete(itemId).as(())

  def update(item: Item): EitherT[F, ItemNotFoundError.type, Item] =
    for {
      _ <- validation.exists(item.id)
      saved <- EitherT.fromOptionF(itemRepo.update(item), ItemNotFoundError)
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
