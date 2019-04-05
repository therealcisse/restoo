package name.amadoucisse.restoo
package service

import repository.ItemRepository
import domain.items.{ Category, Item, ItemId }
import http.{ Page, SortBy }

final class ItemService[F[_]](itemRepo: ItemRepository[F]) {
  def createItem(item: Item): F[Unit] = itemRepo.create(item)

  def getItem(itemId: ItemId): F[Item] =
    itemRepo.get(itemId)

  def deleteItem(itemId: ItemId): F[Unit] = itemRepo.delete(itemId)

  def update(item: Item): F[Unit] = itemRepo.update(item)

  def list(category: Option[Category], orderBy: Seq[SortBy], page: Page): F[Vector[Item]] =
    itemRepo.list(category, orderBy, page)
}

object ItemService {
  def apply[F[_]](repository: ItemRepository[F]): ItemService[F] =
    new ItemService[F](repository)
}
