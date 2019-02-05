package name.amadoucisse.restoo
package infra
package repository.doobie
package queries

import doobie._
import doobie.implicits._
import cats.implicits._
import domain.DateTime
import domain.items._
import http.{ OrderBy, Page, SortBy }

private[doobie] object ItemQueries extends SQLCommon {
  def insert(item: Item): Update0 = sql"""
    INSERT INTO items (id, name, price_in_cents, currency, category, created_at, updated_at)
    VALUES (${item.id}, ${item.name}, ${item.price.amountInCents}, ${item.price.currency}, ${item.category}, ${item.createdAt}, ${item.updatedAt})
  """.update

  def touch(id: ItemId, now: DateTime): Update0 = sql"""
    UPDATE items
    SET
      updated_at = $now
    WHERE id = $id
  """.update

  def update(item: Item): Update0 = sql"""
    UPDATE items
    SET
      name = ${item.name},
      price_in_cents = ${item.price.amountInCents},
      currency = ${item.price.currency},
      category = ${item.category},
      updated_at = ${item.updatedAt}
    WHERE id = ${item.id}
  """.update

  def select(itemId: ItemId): Query0[Item] = sql"""
    SELECT
      name,
      price_in_cents,
      currency,
      category,
      created_at,
      updated_at,
      id
    FROM items
    WHERE id = $itemId
  """.query

  def byName(name: Name): Query0[Item] = sql"""
    SELECT
      name,
      price_in_cents,
      currency,
      category,
      created_at,
      updated_at,
      id
    FROM items
    WHERE name = $name
  """.query

  def delete(itemId: ItemId): Update0 = sql"""
    DELETE FROM items WHERE id = $itemId
  """.update

  def selectAll(category: Option[Category], sort: Seq[SortBy], page: Page): Query0[Item] = {

    val baseSQL = sql"""
    SELECT
      name,
      price_in_cents,
      currency,
      category,
      created_at,
      updated_at,
      id
    FROM items
    """

    val conds = Fragments.whereAndOpt(
      category.map(c ⇒ fr"category = $c"),
      page.marker.map(value ⇒ fr"created_at > $value")
    )

    val getOrderBySQL: SortBy ⇒ Fragment = {
      case SortBy(name, OrderBy.Ascending)  ⇒ Fragment.const(s"$name ASC")
      case SortBy(name, OrderBy.Descending) ⇒ Fragment.const(s"$name DESC")
    }

    val orderBy =
      if (sort.isEmpty) Fragment.empty
      else fr"ORDER BY" ++ sort.toList.map(getOrderBySQL).intercalate(fr",")

    val limitSQL = page.limit match {
      case Some(l) ⇒ Fragment.const(s"LIMIT $l")
      case None    ⇒ Fragment.empty
    }

    (baseSQL ++ conds ++ orderBy ++ limitSQL).query
  }
}
