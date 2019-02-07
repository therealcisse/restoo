package name.amadoucisse.restoo
package infra
package repository.doobie
package queries

import doobie._
import doobie.implicits._
import domain.entries._
import domain.items.ItemId

private[doobie] object EntryQueries extends SQLCommon {

  def insert(entry: Entry): Update0 = sql"""
    INSERT INTO entries (id, item_id, timestamp, delta)
    VALUES (${entry.id}, ${entry.itemId}, ${entry.timestamp}, ${entry.delta})
  """.update

  def count(itemId: ItemId): Query0[Option[Long]] = sql"""
    SELECT
      SUM(delta) as quantity
    FROM entries
    WHERE item_id = $itemId
  """.query[Option[Long]]

}
