package name.amadoucisse.restoo
package infra
package repository.inmemory

import java.util.Random

import cats.effect.Sync
import cats.implicits._
import domain.items._
import domain.AppError
import http.SortBy

import scala.collection.concurrent.TrieMap

final class ItemRepositoryInMemoryInterpreter[F[_]](implicit F: Sync[F]) extends ItemRepositoryAlgebra[F] {

  private val cache = new TrieMap[ItemId, Item]
  private val random = new Random

  def create(item: Item): F[Item] =
    if (cache.values.exists(u ⇒ u.name == item.name)) {
      F.raiseError(AppError.itemAlreadyExists(item))
    } else {
      val id = ItemId(random.nextInt.abs)
      val toSave = item.copy(id = id.some)
      cache += (id → toSave)
      toSave.pure[F]
    }

  def update(item: Item): F[Item] =
    item.id match {
      case Some(id) ⇒
        if (cache.exists(it ⇒ !item.id.contains(it._1) && it._2.name == item.name))
          F.raiseError(AppError.itemAlreadyExists(item))
        else {
          cache.update(id, item)
          item.pure[F]
        }

      case None ⇒ F.raiseError(AppError.itemNotFound)
    }

  def get(id: ItemId): F[Item] =
    cache.get(id) match {
      case Some(item) ⇒ item.pure[F]
      case None       ⇒ F.raiseError(AppError.itemNotFound)
    }

  def findByName(name: Name): F[Item] =
    cache.values.find(u ⇒ u.name == name) match {
      case Some(item) ⇒ item.pure[F]
      case None       ⇒ F.raiseError(AppError.itemNotFound)
    }

  def delete(itemId: ItemId): F[Unit] = {
    cache.remove(itemId)
    ().pure[F]
  }

  def list(category: Option[Category], orderBy: Seq[SortBy]): fs2.Stream[F, Item] = {
    val filtered = category match {
      case Some(value) ⇒ cache.values.filter(_.category == value)
      case None        ⇒ cache.values
    }

    fs2.Stream.emits(filtered.toVector)
  }
}

object ItemRepositoryInMemoryInterpreter {
  def apply[F[_]: Sync]: ItemRepositoryInMemoryInterpreter[F] =
    new ItemRepositoryInMemoryInterpreter[F]
}
