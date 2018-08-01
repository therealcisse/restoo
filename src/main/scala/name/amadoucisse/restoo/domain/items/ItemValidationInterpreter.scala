package name.amadoucisse.restoo
package domain
package items

import cats.Monad
import cats.syntax.functor._
import cats.syntax.applicative._
import cats.syntax.either._

final class ItemValidationInterpreter[F[_]: Monad](itemRepo: ItemRepositoryAlgebra[F])
    extends ItemValidationAlgebra[F] {
  def doesNotExist(item: Item): F[ItemAlreadyExistsError Either Unit] =
    itemRepo.findByName(item.name).map {
      case Left(_) => Right(())
      case Right(_) => Left(ItemAlreadyExistsError(item))
    }

  def exists(itemId: Option[ItemId]): F[ItemNotFoundError.type Either Item] =
    itemId match {
      case Some(id) => itemRepo.get(id)
      case None => Either.left[ItemNotFoundError.type, Item](ItemNotFoundError).pure[F]
    }

}

object ItemValidationInterpreter {
  def apply[F[_]: Monad](itemRepo: ItemRepositoryAlgebra[F]) =
    new ItemValidationInterpreter(itemRepo)
}
