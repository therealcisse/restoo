package name.amadoucisse.restoo
package domain
package items

trait ItemValidationAlgebra[F[_]] {
  def doesNotExist(item: Item): F[ItemAlreadyExistsError Either Unit]

  def exists(itemId: Option[ItemId]): F[ItemNotFoundError.type Either Item]

}
