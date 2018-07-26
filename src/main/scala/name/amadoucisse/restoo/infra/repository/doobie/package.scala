package name.amadoucisse.restoo
package infra
package repository

import java.sql.Timestamp

import java.time.Instant

import domain.OccurredAt
import domain.items.{Category, Cents, ItemId, Name}

package object doobie {
  import _root_.doobie.Meta

  implicit val CategoryMeta: Meta[Category] =
    Meta[String].xmap[Category](x => Category(x), _.value)

  implicit val NameMeta: Meta[Name] =
    Meta[String].xmap[Name](x => Name(x), _.value)

  implicit val CentsMeta: Meta[Cents] =
    Meta[Int].xmap[Cents](x => Cents(x), _.value)

  implicit val ItemIdMeta: Meta[ItemId] =
    Meta[Long].xmap[ItemId](x => ItemId(x), _.value)

  /* We require conversion for date time */
  implicit val DateTimeMeta: Meta[Instant] =
    Meta[Timestamp].xmap(
      ts => ts.toInstant,
      dt => Timestamp.from(dt)
    )

  implicit val OccurredAtMeta: Meta[OccurredAt] =
    Meta[Instant].xmap[OccurredAt](x => OccurredAt(x), _.value)
}
