package name.amadoucisse.restoo
package infra
package repository.doobie

import java.sql.Timestamp

import java.time.Instant

import doobie.Meta

import domain.DateTime
import domain.items.{ Category, ItemId, Name }
import domain.entries.{ Delta, EntryId }

private trait SQLCommon {

  implicit val CategoryMeta: Meta[Category] =
    Meta[String].timap[Category](Category(_))(_.value)

  implicit val NameMeta: Meta[Name] =
    Meta[String].timap[Name](Name(_))(_.value)

  implicit val ItemIdMeta: Meta[ItemId] =
    Meta[Long].timap[ItemId](ItemId(_))(_.value)

  implicit val EntryIdMeta: Meta[EntryId] =
    Meta[Long].timap[EntryId](EntryId(_))(_.value)

  implicit val DeltaMeta: Meta[Delta] =
    Meta[Int].timap[Delta](Delta(_))(_.value)

  /* We require conversion for date time */
  implicit val DateTimeMeta: Meta[Instant] =
    Meta[Timestamp].timap(_.toInstant)(Timestamp.from)

  implicit val OccurredAtMeta: Meta[DateTime] =
    Meta[Instant].timap[DateTime](DateTime(_))(_.value)
}
