package name.amadoucisse.restoo
package infra
package repository.doobie
package queries

import doobie._
import doobie.implicits._

private[doobie] object IdQueries extends SQLCommon {

  def newItemId: Query0[Long] = sql"""
    SELECT nextval('items_seq')
  """.query[Long]

  def newEntryId: Query0[Long] = sql"""
    SELECT nextval('entries_seq')
  """.query[Long]

}
