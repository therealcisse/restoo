package name.amadoucisse.restoo
package domain
package items

import cats.{Applicative, Monad}
import cats.syntax.functor._

final class ItemValidationInterpreter[F[_]: Monad](itemRepo: ItemRepositoryAlgebra[F])
    extends ItemValidationAlgebra[F] {

  def exists(itemId: Option[ItemId]): F[Boolean] =
    itemId match {
      case Some(id) => itemRepo.get(id).map(_.isDefined)
      case None => Applicative[F].pure(false)
    }

}

object ItemValidationInterpreter {
  def apply[F[_]: Monad](itemRepo: ItemRepositoryAlgebra[F]): ItemValidationInterpreter[F] =
    new ItemValidationInterpreter(itemRepo)
}
