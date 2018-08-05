package name.amadoucisse.restoo
package domain

import cats.data.NonEmptyList
import items.Item
import utils.Validator

sealed abstract class AppError extends Product with Serializable

final case class ItemAlreadyExists(item: Item) extends AppError
case object ItemNotFound extends AppError

case object ItemOutOfStock extends AppError

final case class ErrorListing(errors: NonEmptyList[Validator.FieldError]) extends AppError
