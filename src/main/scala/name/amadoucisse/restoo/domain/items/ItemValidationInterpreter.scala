package name.amadoucisse.restoo
package domain
package items

import cats._
import cats.implicits._
import cats.data.EitherT

final class ItemValidationInterpreter[F[_]: Monad](itemRepo: ItemRepositoryAlgebra[F])
    extends ItemValidationAlgebra[F] {
  def doesNotExist(item: Item): EitherT[F, ItemAlreadyExistsError, Unit] = EitherT {
    itemRepo.findByName(item.name).map {
      case None => Right(())
      case Some(_) => Left(ItemAlreadyExistsError(item))
    }
  }

  def exists(itemId: Option[ItemId]): EitherT[F, ItemNotFoundError.type, Unit] =
    EitherT {
      itemId
        .map { id =>
          itemRepo.get(id).map {
            case Some(_) => Right(())
            case _ => Left(ItemNotFoundError)
          }
        }
        .getOrElse(
          Either.left[ItemNotFoundError.type, Unit](ItemNotFoundError).pure[F]
        )
    }

}

object ItemValidationInterpreter {
  def apply[F[_]: Monad](itemRepo: ItemRepositoryAlgebra[F]) =
    new ItemValidationInterpreter(itemRepo)
}
