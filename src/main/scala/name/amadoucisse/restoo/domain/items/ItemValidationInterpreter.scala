package name.amadoucisse.restoo
package domain
package items

import cats.Monad
import cats.syntax.functor._
import cats.syntax.applicative._

final class ItemValidationInterpreter[F[_]: Monad](itemRepo: ItemRepositoryAlgebra[F])
    extends ItemValidationAlgebra[F] {

  def exists(itemId: Option[ItemId]): F[Boolean] =
    itemId match {
      case Some(id) => itemRepo.get(id).map(_.isDefined)
      case None => false.pure[F]
    }

}

object ItemValidationInterpreter {
  def apply[F[_]: Monad](itemRepo: ItemRepositoryAlgebra[F]) =
    new ItemValidationInterpreter(itemRepo)
}
