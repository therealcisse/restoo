package name.amadoucisse.restoo
package domain

sealed trait ValidationError extends Product with Serializable

final case class ItemAlreadyExistsError(name: String) extends ValidationError
final case object ItemNotFoundError extends ValidationError
