package name.amadoucisse.restoo

import cats.effect.{ Sync, Timer }
import cats.temp.par._

import service.{ Id, Items, Stocks }
import repository.{ EntryRepository, IdRepository, ItemRepository }

import cats.Applicative
import cats.mtl.{ ApplicativeAsk, DefaultApplicativeAsk }

private[restoo] final class Module[F[_]: Sync: Timer: Par](
    idRepo: IdRepository[F],
    itemRepo: ItemRepository[F],
    entryRepo: EntryRepository[F],
) {

  implicit val id: ApplicativeAsk[F, Id[F]] = new DefaultApplicativeAsk[F, Id[F]] {
    val applicative: Applicative[F] = Applicative[F]
    def ask: F[Id[F]] =
      Sync[F].pure(new Id[F] {
        def id: Id.Service[F] = Id.Live[F](idRepo)
      })
  }

  implicit val items: ApplicativeAsk[F, Items[F]] = new DefaultApplicativeAsk[F, Items[F]] {
    val applicative: Applicative[F] = Applicative[F]
    def ask: F[Items[F]] =
      Sync[F].pure(new Items[F] {
        def items: Items.Service[F] = Items.Live[F](itemRepo)
      })
  }

  implicit val stocks: ApplicativeAsk[F, Stocks[F]] = new DefaultApplicativeAsk[F, Stocks[F]] {
    val applicative: Applicative[F] = Applicative[F]
    def ask: F[Stocks[F]] =
      Sync[F].pure(new Stocks[F] {
        def stocks: Stocks.Service[F] = Stocks.Live[F](entryRepo, itemRepo, idRepo)
      })
  }

}
