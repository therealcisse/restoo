package name.amadoucisse.restoo
package infra
package repository.inmemory

import java.util.Random

import cats._
import cats.implicits._

import domain.ItemNotFoundError
import domain.items._

import scala.collection.concurrent.TrieMap

final class ItemRepositoryInMemoryInterpreter[F[_]: Applicative] extends ItemRepositoryAlgebra[F] {

  private val cache = new TrieMap[ItemId, Item]
  private val random = new Random

  def create(item: Item): F[Item] = {
    val id = ItemId(random.nextInt.abs)
    val toSave = item.copy(id = id.some)
    cache += (id -> toSave)
    toSave.pure[F]
  }

  def update(item: Item): F[ItemNotFoundError.type Either Item] =
    Either
      .fromOption(item.id, ItemNotFoundError)
      .map { id =>
        cache.update(id, item)
        item
      }
      .pure[F]

  def get(id: ItemId): F[ItemNotFoundError.type Either Item] =
    Either.fromOption(cache.get(id), ItemNotFoundError).pure[F]

  def findByName(name: Name): F[ItemNotFoundError.type Either Item] =
    Either.fromOption(cache.values.find(u => u.name == name), ItemNotFoundError).pure[F]

  def delete(itemId: ItemId): F[ItemNotFoundError.type Either Unit] =
    Either.fromOption(cache.remove(itemId), ItemNotFoundError).map(_ => ()).pure[F]

  def list(): fs2.Stream[F, Item] = fs2.Stream.emits(cache.values.toVector.sortBy(_.name.value))
}

object ItemRepositoryInMemoryInterpreter {
  def apply[F[_]: Applicative]: ItemRepositoryInMemoryInterpreter[F] =
    new ItemRepositoryInMemoryInterpreter[F]
}
