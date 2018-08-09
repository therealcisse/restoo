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

  final case class InvalidEntity(errors: NonEmptyList[Validator.FieldError]) extends AppError

  def itemAlreadyExists(item: Item): AppError = ItemAlreadyExists(item)
  def itemNotFound: AppError = ItemNotFound
  def itemOutOfStock: AppError = ItemOutOfStock
  def invalidEntity(errors: NonEmptyList[Validator.FieldError]): AppError =
    InvalidEntity(errors)
}
