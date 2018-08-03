package name.amadoucisse.restoo
package domain
package items

trait ItemValidationAlgebra[F[_]] {
  def doesNotExist(item: Item): F[AppError Either Unit]

  def exists(itemId: Option[ItemId]): F[Option[Item]]

}
