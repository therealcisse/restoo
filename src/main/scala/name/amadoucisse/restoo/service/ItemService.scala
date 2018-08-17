package name.amadoucisse.restoo
package service

import cats._
import cats.implicits._
import domain.items.{Category, Item, ItemId, ItemRepositoryAlgebra}
import domain.AppError
import http.SortBy

final class ItemService[F[_]: Monad](itemRepo: ItemRepositoryAlgebra[F]) {

  def createItem(item: Item): F[AppError Either Item] =
    itemRepo.create(item)

  def getItem(itemId: ItemId): F[AppError Either Item] =
    itemRepo.get(itemId).map(_.toRight(AppError.itemNotFound))

  def deleteItem(itemId: ItemId): F[Unit] = itemRepo.delete(itemId)

  def update(item: Item): F[AppError Either Item] = itemRepo.update(item)

  def list(category: Option[Category], orderBy: Seq[SortBy]): fs2.Stream[F, Item] =
    itemRepo.list(category, orderBy)
}

object ItemService {
  def apply[F[_]: Monad](repository: ItemRepositoryAlgebra[F]): ItemService[F] =
    new ItemService[F](repository)
}
