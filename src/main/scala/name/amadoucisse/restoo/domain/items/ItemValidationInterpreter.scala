package name.amadoucisse.restoo
package domain
package items

import cats.Monad
import cats.syntax.functor._
import cats.syntax.applicative._
import cats.syntax.option._
import cats.syntax.either._

final class ItemValidationInterpreter[F[_]: Monad](itemRepo: ItemRepositoryAlgebra[F])
    extends ItemValidationAlgebra[F] {
  def doesNotExist(item: Item): F[AppError Either Unit] =
    itemRepo.findByName(item.name).map {
      case None => ().asRight
      case Some(_) => ItemAlreadyExists(item).asLeft
    }

  def exists(itemId: Option[ItemId]): F[Option[Item]] =
    itemId match {
      case Some(id) => itemRepo.get(id)
      case None => none[Item].pure[F]
    }

}

object ItemValidationInterpreter {
  def apply[F[_]: Monad](itemRepo: ItemRepositoryAlgebra[F]) =
    new ItemValidationInterpreter(itemRepo)
}
