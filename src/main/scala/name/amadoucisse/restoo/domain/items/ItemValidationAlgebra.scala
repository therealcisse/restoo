package name.amadoucisse.restoo
package domain
package items

trait ItemValidationAlgebra[F[_]] {
  def exists(itemId: Option[ItemId]): F[Boolean]

}
