package name.amadoucisse.restoo
package domain

import items.Item

sealed trait ValidationError extends Product with Serializable

final case class ItemAlreadyExistsError(item: Item) extends ValidationError
case object ItemNotFoundError extends ValidationError

case object ItemOutOfStockError extends ValidationError

final case class NoStockError(item: Item) extends ValidationError
