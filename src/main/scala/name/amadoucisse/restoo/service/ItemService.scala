package name.amadoucisse.restoo
package service

import cats._
import cats.implicits._
import cats.data.EitherT

import domain.items.{Item, ItemId, ItemRepositoryAlgebra}
import domain.{ItemAlreadyExistsError, ItemNotFoundError}

final class ItemService[F[_]: Monad](itemRepo: ItemRepositoryAlgebra[F]) {

  def createItem(item: Item): EitherT[F, ItemAlreadyExistsError, Item] = {
    val action: EitherT[F, ItemAlreadyExistsError, Option[Item]] = EitherT
      .liftF(itemRepo.findByName(item.name))

    action.flatMap {
      case Some(_) => EitherT.leftT(ItemAlreadyExistsError(item.name.value))
      case None => EitherT.right(itemRepo.create(item))
    }
  }

  def getItem(itemId: ItemId): EitherT[F, ItemNotFoundError.type, Item] =
    EitherT.fromOptionF(itemRepo.get(itemId), ItemNotFoundError)

  def deleteItem(itemId: ItemId): F[Unit] = itemRepo.delete(itemId).as(())

  def update(item: Item): EitherT[F, ItemNotFoundError.type, Item] =
    EitherT.fromOptionF(itemRepo.update(item), ItemNotFoundError)

  def list(): F[Vector[Item]] =
    itemRepo.list()
}

object ItemService {
  def apply[F[_]: Monad](repository: ItemRepositoryAlgebra[F]): ItemService[F] =
    new ItemService[F](repository)
}
