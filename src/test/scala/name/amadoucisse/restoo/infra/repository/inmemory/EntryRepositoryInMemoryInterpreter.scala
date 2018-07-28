package name.amadoucisse.restoo
package infra
package repository.inmemory

import java.util.Random

import cats._
import cats.implicits._

import domain.entries._
import domain.items.ItemId

import scala.collection.concurrent.TrieMap

final class EntryRepositoryInMemoryInterpreter[F[_]: Applicative]
    extends EntryRepositoryAlgebra[F] {

  private val cache = new TrieMap[EntryId, Entry]
  private val random = new Random

  def create(entry: Entry): F[Entry] = {
    val id = EntryId(random.nextInt.abs)
    val toSave = entry.copy(id = id.some)
    cache += (id -> toSave)
    toSave.pure[F]
  }

  def count(id: ItemId): F[Option[Int]] = {
    val itemEntries = cache.filter(_._2.itemId.value == id.value)

    if (itemEntries.isEmpty) Option.empty[Int].pure[F]
    else itemEntries.foldLeft(0)(_ + _._2.delta.value).some.pure[F]
  }
}

object EntryRepositoryInMemoryInterpreter {
  def apply[F[_]: Applicative]: EntryRepositoryInMemoryInterpreter[F] =
    new EntryRepositoryInMemoryInterpreter[F]
}
