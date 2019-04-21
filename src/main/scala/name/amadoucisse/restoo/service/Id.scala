package name.amadoucisse.restoo
package service

import domain.items.ItemId
import domain.entries.EntryId
import repository.IdRepository

import cats.syntax.flatMap._
import cats.FlatMap
import cats.mtl.ApplicativeAsk

trait Id[F[_]] {
  def id: Id.Service[F]
}

object Id {

  trait Service[F[_]] {
    def newItemId: F[ItemId]
    def newEntryId: F[EntryId]
  }

  final case class Live[F[_]](idRepo: IdRepository[F]) extends Service[F] {
    def newItemId: F[ItemId] = idRepo.newItemId

    def newEntryId: F[EntryId] =
      idRepo.newEntryId

  }

  def newItemId[F[_]: FlatMap](implicit A: ApplicativeAsk[F, Id[F]]): F[ItemId] = A.ask.flatMap(_.id.newItemId)

  def newEntryId[F[_]: FlatMap](implicit A: ApplicativeAsk[F, Id[F]]): F[EntryId] = A.ask.flatMap(_.id.newEntryId)
}
