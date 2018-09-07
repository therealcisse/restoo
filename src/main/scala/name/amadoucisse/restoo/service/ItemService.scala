package name.amadoucisse.restoo
package service

import cats.syntax.flatMap._
import cats.syntax.functor._
import cats.effect.Sync
import domain.items.{ Category, Item, ItemId, ItemRepositoryAlgebra }
import http.SortBy

final class ItemService[F[_]](itemRepo: ItemRepositoryAlgebra[F])(implicit F: Sync[F]) {
  def createItem(item: Item): F[Item] =
    for {
      _ ← F.delay(scribe.info(s"Creating item ${item.name}"))
      item ← itemRepo.create(item)
      _ ← F.delay(scribe.info(s"Created item ${item.name} with id : ${item.id}"))
    } yield item

  def getItem(itemId: ItemId): F[Item] =
    itemRepo.get(itemId)

  def deleteItem(itemId: ItemId): F[Unit] =
    for {
      _ ← F.delay(scribe.info(s"Deleting item : $itemId"))
      _ ← itemRepo.delete(itemId)
      _ ← F.delay(scribe.info(s"Deleted item : $itemId"))
    } yield {}

  def update(item: Item): F[Item] =
    for {
      _ ← F.delay(scribe.info(s"Updating item ${item.name}"))
      item ← itemRepo.update(item)
      _ ← F.delay(scribe.info(s"Updated item ${item.name} with id : ${item.id}"))
    } yield item

  def list(category: Option[Category], orderBy: Seq[SortBy]): fs2.Stream[F, Item] =
    itemRepo.list(category, orderBy)
}

object ItemService {
  def apply[F[_]: Sync](repository: ItemRepositoryAlgebra[F]): ItemService[F] =
    new ItemService[F](repository)
}
