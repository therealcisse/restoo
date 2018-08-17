package name.amadoucisse.restoo
package infra
package repository.inmemory

import java.util.Random

import cats._
import cats.data.EitherT
import cats.implicits._
import domain.items._
import domain.AppError
import http.SortBy

import scala.collection.concurrent.TrieMap

final class ItemRepositoryInMemoryInterpreter[F[_]: Monad] extends ItemRepositoryAlgebra[F] {

  private val cache = new TrieMap[ItemId, Item]
  private val random = new Random

  def create(item: Item): F[AppError Either Item] = findByName(item.name).map {
    case Some(_) => AppError.itemAlreadyExists(item).asLeft
    case None =>
      val id = ItemId(random.nextInt.abs)
      val toSave = item.copy(id = id.some)
      cache += (id -> toSave)
      toSave.asRight

  }

  def update(item: Item): F[AppError Either Item] =
    EitherT
      .fromOption[F](item.id, AppError.itemNotFound)
      .subflatMap { id =>
        if (cache.exists(it => !item.id.contains(it._1) && it._2.name == item.name))
          AppError.duplicateItem(item.name).asLeft[Item]
        else {
          cache.update(id, item)
          item.asRight[AppError]
        }
      }
      .value

  def get(id: ItemId): F[Option[Item]] =
    cache.get(id).pure[F]

  def findByName(name: Name): F[Option[Item]] =
    cache.values.find(u => u.name == name).pure[F]

  def delete(itemId: ItemId): F[Unit] = {
    cache.remove(itemId)
    ().pure[F]
  }

  def list(category: Option[Category], orderBy: Seq[SortBy]): fs2.Stream[F, Item] =
    fs2.Stream.emits {
      val filtered = category match {
        case Some(c) => cache.values.filter(_.category == c)
        case None => cache.values
      }

      filtered.toVector
    }
}

object ItemRepositoryInMemoryInterpreter {
  def apply[F[_]: Monad]: ItemRepositoryInMemoryInterpreter[F] =
    new ItemRepositoryInMemoryInterpreter[F]
}
