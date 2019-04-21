package name.amadoucisse.restoo
package service

import cats.Applicative
import cats.effect.IO
import domain.items._
import domain.entries._
import repository.IdRepository
import common.IOAssertion
import org.scalatest.{ MustMatchers, WordSpec }
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks

import service.Id._

import cats.mtl.{ ApplicativeAsk, DefaultApplicativeAsk }

class IdServiceSpec extends WordSpec with MustMatchers with ScalaCheckDrivenPropertyChecks {
  val existingItemId = ItemId(1L)
  val existingEntryId = EntryId(10L)

  "item" when {

    "generate item id is called" should {
      "generates a new item id" in new Context {

        val op =
          newItemId[IO]

        IOAssertion {

          for {
            itemId ← op
            _ = itemId mustBe existingItemId
          } yield ()
        }
      }
    }

  }

  "entry" when {

    "generate entry id is called" should {
      "generates a new entry id" in new Context {

        val op =
          newEntryId[IO]

        IOAssertion {

          for {
            entryId ← op
            _ = entryId mustBe existingEntryId
          } yield ()
        }
      }
    }

  }

  final class IdRepositoryImpl extends IdRepository[IO] {
    def newItemId: IO[ItemId] = IO(existingItemId)
    def newEntryId: IO[EntryId] = IO(existingEntryId)
  }

  trait Context extends IOExecution {
    val idRepo = new IdRepositoryImpl

    implicit val idInstance: ApplicativeAsk[IO, Id[IO]] = new DefaultApplicativeAsk[IO, Id[IO]] {
      val applicative: Applicative[IO] = Applicative[IO]
      def ask: IO[Id[IO]] =
        IO.pure(new Id[IO] {
          def id: Id.Service[IO] = Id.Live[IO](idRepo)
        })
    }
  }
}
