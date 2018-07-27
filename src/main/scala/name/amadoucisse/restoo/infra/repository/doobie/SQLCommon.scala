package name.amadoucisse.restoo
package infra
package repository.doobie

import java.sql.Timestamp

import java.time.Instant

import doobie.Meta

import domain.OccurredAt
import domain.items.{Category, Cents, ItemId, Name}
import domain.entries.{Delta, EntryId}

private trait SQLCommon {

  implicit val CategoryMeta: Meta[Category] =
    Meta[String].xmap[Category](x => Category(x), _.value)

  implicit val NameMeta: Meta[Name] =
    Meta[String].xmap[Name](x => Name(x), _.value)

  implicit val CentsMeta: Meta[Cents] =
    Meta[Int].xmap[Cents](x => Cents(x), _.value)

  implicit val ItemIdMeta: Meta[ItemId] =
    Meta[Int].xmap[ItemId](x => ItemId(x), _.value)

  implicit val EntryIdMeta: Meta[EntryId] =
    Meta[Int].xmap[EntryId](x => EntryId(x), _.value)

  implicit val DeltaMeta: Meta[Delta] =
    Meta[Int].xmap[Delta](x => Delta(x), _.value)

  /* We require conversion for date time */
  implicit val DateTimeMeta: Meta[Instant] =
    Meta[Timestamp].xmap(
      ts => ts.toInstant,
      dt => Timestamp.from(dt)
    )

  implicit val OccurredAtMeta: Meta[OccurredAt] =
    Meta[Instant].xmap[OccurredAt](x => OccurredAt(x), _.value)
}
