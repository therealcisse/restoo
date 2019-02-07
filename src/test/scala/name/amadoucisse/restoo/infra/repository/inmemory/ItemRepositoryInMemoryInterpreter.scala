package name.amadoucisse.restoo
package infra
package repository.inmemory

import cats.effect.Sync
import cats.implicits._
import domain.items._
import domain.AppError
import http.{ Page, SortBy }

import cats.effect.concurrent.Ref
import cats.effect.Sync

final class ItemRepositoryInMemoryInterpreter[F[_]: Sync](ref: Ref[F, Map[ItemId, Item]])
    extends ItemRepositoryAlgebra[F] {

  def create(item: Item): F[Unit] = {

    val step: Map[ItemId, Item] ⇒ (Map[ItemId, Item], F[Unit]) = { map ⇒
      if (map.values.exists(u ⇒ u.name == item.name)) {
        (map, Sync[F].raiseError(AppError.itemAlreadyExists(item)))
      } else {
        (map.updated(item.id, item), Sync[F].pure(()))
      }
    }

    ref.modify(step).flatten
  }

  def update(item: Item): F[Unit] = {
    val step: Map[ItemId, Item] ⇒ (Map[ItemId, Item], F[Unit]) = { map ⇒
      if (map.exists { case (key, value) ⇒ item.id != key && value.name == item.name })
        (map, Sync[F].raiseError(AppError.itemAlreadyExists(item)))
      else {
        (map.updated(item.id, item), Sync[F].pure(()))
      }

    }
    ref.modify(step).flatten

  }

  def get(id: ItemId): F[Item] = ref.get.flatMap { map ⇒
    map.get(id) match {
      case Some(item) ⇒ item.pure[F]
      case None       ⇒ Sync[F].raiseError(AppError.itemNotFound)
    }
  }

  def findByName(name: Name): F[Item] = ref.get.flatMap { map ⇒
    map.values.find(u ⇒ u.name == name) match {
      case Some(item) ⇒ item.pure[F]
      case None       ⇒ Sync[F].raiseError(AppError.itemNotFound)
    }
  }

  def delete(itemId: ItemId): F[Unit] = ref.update { map ⇒
    map - itemId
  }

  def list(category: Option[Category], orderBy: Seq[SortBy], page: Page): fs2.Stream[F, Item] =
    fs2.Stream.eval(ref.get).flatMap { map ⇒
      val filtered = category match {
        case Some(value) ⇒ map.values.filter(_.category == value)
        case None        ⇒ map.values
      }

      def paginated: (Vector[Item], Page) ⇒ Vector[Item] = {
        case (list, Page(marker, limit)) ⇒
          val ls = marker match {
            case Some(m) ⇒ list.dropWhile(_.createdAt.value.compareTo(m) <= 0)
            case None    ⇒ list
          }

          limit match {
            case Some(n) ⇒ ls.take(n)
            case None    ⇒ ls
          }
      }

      fs2.Stream.emits(paginated(filtered.toVector, page))

    }

}

object ItemRepositoryInMemoryInterpreter {
  def apply[F[_]: Sync]: F[ItemRepositoryInMemoryInterpreter[F]] =
    Ref.of[F, Map[ItemId, Item]](Map.empty).map(new ItemRepositoryInMemoryInterpreter[F](_))
}
