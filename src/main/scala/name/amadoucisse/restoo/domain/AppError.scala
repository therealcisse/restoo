package name.amadoucisse.restoo
package domain

import cats.data.NonEmptyList
import items.Item
import utils.Validation

sealed abstract class AppError(val message: String) extends Exception(message)

object AppError {
  final case object InvalidJsonPatch extends AppError("Invalid json patch")
  final case class ItemAlreadyExists(item: Item) extends AppError("Item already exists")
  case object ItemNotFound extends AppError("Item not found")

  case object ItemOutOfStock extends AppError("Item out of stock")

  final case class ValidationFailed(errors: NonEmptyList[Validation.FieldError]) extends AppError("Validation failed")

  def invalidJsonPatch: AppError = InvalidJsonPatch
  def itemAlreadyExists(item: Item): AppError = ItemAlreadyExists(item)
  def itemNotFound: AppError = ItemNotFound
  def itemOutOfStock: AppError = ItemOutOfStock
  def validationFailed(errors: NonEmptyList[Validation.FieldError]): AppError =
    ValidationFailed(errors)
}
