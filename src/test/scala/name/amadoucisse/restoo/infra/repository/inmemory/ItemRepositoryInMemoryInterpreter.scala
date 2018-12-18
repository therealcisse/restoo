package name.amadoucisse.restoo
package infra
package repository.inmemory

import scala.util.Random

import cats.effect.Sync
import cats.implicits._
import domain.items._
import domain.AppError
import http.SortBy

import cats.effect.concurrent.Ref
import cats.effect.Sync

final class ItemRepositoryInMemoryInterpreter[F[_]: Sync](state: Ref[F, Map[ItemId, Item]])
    extends ItemRepositoryAlgebra[F] {

  @SuppressWarnings(Array("org.wartremover.warts.Throw"))
  def create(item: Item): F[Item] = state.modify { map ⇒
    if (map.values.exists(u ⇒ u.name == item.name)) {
      throw AppError.itemAlreadyExists(item)
    } else {
      val id = ItemId(Random.nextInt.abs)
      val value = item.copy(id = id.some)
      (map.updated(id, value), value)
    }
  }

  @SuppressWarnings(Array("org.wartremover.warts.Throw"))
  def update(item: Item): F[Item] =
    item.id match {
      case Some(id) ⇒
        state.modify { map ⇒
          if (map.exists(it ⇒ !item.id.contains(it._1) && it._2.name == item.name))
            throw AppError.itemAlreadyExists(item)
          else {
            (map.updated(id, item), item)
          }
        }

      case None ⇒ Sync[F].raiseError(AppError.itemNotFound)
    }

  def get(id: ItemId): F[Item] = state.get.flatMap { map ⇒
    map.get(id) match {
      case Some(item) ⇒ item.pure[F]
      case None       ⇒ Sync[F].raiseError(AppError.itemNotFound)
    }
  }

  def findByName(name: Name): F[Item] = state.get.flatMap { map ⇒
    map.values.find(u ⇒ u.name == name) match {
      case Some(item) ⇒ item.pure[F]
      case None       ⇒ Sync[F].raiseError(AppError.itemNotFound)
    }
  }

  def delete(itemId: ItemId): F[Unit] = state.update { map ⇒
    map - itemId
  }

  def list(category: Option[Category], orderBy: Seq[SortBy]): fs2.Stream[F, Item] = {

    val xs = state.get.map { map ⇒
      val filtered = category match {
        case Some(value) ⇒ map.values.filter(_.category == value)
        case None        ⇒ map.values
      }

      fs2.Stream.emits(filtered.toVector)
    }

    fs2.Stream.eval(xs).flatten
  }
}

object ItemRepositoryInMemoryInterpreter {
  def apply[F[_]: Sync]: F[ItemRepositoryInMemoryInterpreter[F]] =
    for {
      ref ← Ref.of[F, Map[ItemId, Item]](Map.empty)
    } yield new ItemRepositoryInMemoryInterpreter[F](ref)
}
