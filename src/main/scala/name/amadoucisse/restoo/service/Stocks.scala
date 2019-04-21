package name.amadoucisse.restoo
package service

import repository.ItemRepository
import domain.items.{ Category, Item, ItemId }
import http.{ Page, SortBy }

import cats.syntax.flatMap._
import cats.FlatMap
import cats.mtl.ApplicativeAsk

trait Items[F[_]] {
  def items: Items.Service[F]
}

object Items {

  trait Service[F[_]] {
    def createItem(item: Item): F[Unit]
    def getItem(itemId: ItemId): F[Item]
    def deleteItem(itemId: ItemId): F[Unit]
    def update(item: Item): F[Unit]
    def list(category: Option[Category], orderBy: Seq[SortBy], page: Page): F[Vector[Item]]
  }

  final case class Live[F[_]](itemRepo: ItemRepository[F]) extends Service[F] {
    def createItem(item: Item): F[Unit] = itemRepo.create(item)

    def getItem(itemId: ItemId): F[Item] =
      itemRepo.get(itemId)

    def deleteItem(itemId: ItemId): F[Unit] = itemRepo.delete(itemId)

    def update(item: Item): F[Unit] = itemRepo.update(item)

    def list(category: Option[Category], orderBy: Seq[SortBy], page: Page): F[Vector[Item]] =
      itemRepo.list(category, orderBy, page)
  }

  def createItem[F[_]: FlatMap](item: Item)(implicit A: ApplicativeAsk[F, Items[F]]): F[Unit] =
    A.ask.flatMap(_.items.createItem(item))

  def getItem[F[_]: FlatMap](itemId: ItemId)(implicit A: ApplicativeAsk[F, Items[F]]): F[Item] =
    A.ask.flatMap(_.items.getItem(itemId))

  def deleteItem[F[_]: FlatMap](itemId: ItemId)(implicit A: ApplicativeAsk[F, Items[F]]): F[Unit] =
    A.ask.flatMap(_.items.deleteItem(itemId))

  def updateItem[F[_]: FlatMap](item: Item)(implicit A: ApplicativeAsk[F, Items[F]]): F[Unit] =
    A.ask.flatMap(_.items.update(item))

  def listItems[F[_]: FlatMap](category: Option[Category], orderBy: Seq[SortBy], page: Page)(
      implicit A: ApplicativeAsk[F, Items[F]]
  ): F[Vector[Item]] = A.ask.flatMap(_.items.list(category, orderBy, page))

}
