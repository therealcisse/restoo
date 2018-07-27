package name.amadoucisse.restoo
package domain
package items

import cats.data.EitherT

trait ItemValidationAlgebra[F[_]] {
  def doesNotExist(item: Item): EitherT[F, ItemAlreadyExistsError, Unit]

  def exists(itemId: Option[ItemId]): EitherT[F, ItemNotFoundError.type, Unit]

}
