package name.amadoucisse.restoo
package domain

import cats.data.NonEmptyList
import items.Item
import utils.Validator

sealed abstract class AppError extends Product with Serializable

final case class ItemAlreadyExistsError(item: Item) extends AppError
case object ItemNotFoundError extends AppError

case object ItemOutOfStockError extends AppError

final case class NoStockError(item: Item) extends AppError

final case class ErrorListing(errors: NonEmptyList[Validator.FieldError]) extends AppError
