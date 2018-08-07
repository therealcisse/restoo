package name.amadoucisse.restoo
package domain

import cats.data.NonEmptyList
import items.Item
import utils.Validator

sealed abstract class AppError extends Product with Serializable

object AppError {
  final case class ItemAlreadyExists(item: Item) extends AppError
  case object ItemNotFound extends AppError

  case object ItemOutOfStock extends AppError

  final case class ErrorListing(errors: NonEmptyList[Validator.FieldError]) extends AppError

  def itemAlreadyExists(item: Item): AppError = ItemAlreadyExists(item)
  def itemNotFound: AppError = ItemNotFound
  def itemOutOfStock: AppError = ItemOutOfStock
  def errorListing(errors: NonEmptyList[Validator.FieldError]): AppError =
    ErrorListing(errors)
}
