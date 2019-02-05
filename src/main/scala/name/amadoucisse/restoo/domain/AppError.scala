package name.amadoucisse.restoo
package domain

import cats.data.NonEmptyChain
import items.Item
import utils.Validation

sealed abstract class AppError(val message: String) extends Exception(message)

object AppError {
  final case class InvalidJsonPatch(override val message: String) extends AppError(message)
  final case class ItemAlreadyExists(item: Item) extends AppError("Item already exists")
  case object ItemNotFound extends AppError("Item not found")

  case object ItemOutOfStock extends AppError("Item out of stock")

  final case class ValidationFailed(errors: NonEmptyChain[Validation.FieldError]) extends AppError("Validation failed")

  def invalidJsonPatch(message: ⇒ String): AppError = InvalidJsonPatch(message)
  def itemAlreadyExists(item: Item): AppError = ItemAlreadyExists(item)
  def itemNotFound: AppError = ItemNotFound
  def itemOutOfStock: AppError = ItemOutOfStock
  def validationFailed(errors: NonEmptyChain[Validation.FieldError]): AppError =
    ValidationFailed(errors)
}
