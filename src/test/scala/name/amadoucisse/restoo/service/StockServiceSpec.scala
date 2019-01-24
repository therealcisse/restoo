package name.amadoucisse.restoo
package service

import cats.implicits._
import cats.effect.IO
import domain.DateTime
import domain.items._
import domain.entries._
import http.{ Page, SortBy }
import common.IOAssertion
import org.scalatest.{ MustMatchers, WordSpec }
import name.amadoucisse.restoo.domain.AppError
import org.scalacheck.Gen
import org.scalatest.prop.GeneratorDrivenPropertyChecks

import java.time.Instant

class StockServiceSpec extends WordSpec with MustMatchers with GeneratorDrivenPropertyChecks {
  val existingItemId = ItemId(1)
  val existingItemCount = 10L

  "add stock" when {

    "item exists" should {
      "add quantity to stock" in new Context {

        val op =
          p.createEntry(existingItemId, Delta(10))

        IOAssertion {

          for {
            stock ← op
            _ = stock.quantity mustBe existingItemCount
          } yield ()
        }
      }
    }

    "item does not exist" should {
      "fail" in new Context {

        val arb = Gen.posNum[Int].filter(_ != existingItemId.value)

        forAll(arb) { id: Int ⇒
          val op =
            p.createEntry(ItemId(id), Delta(50))

          a[AppError.ItemNotFound.type] should be thrownBy IOAssertion {
            op
          }

        }

      }
    }

  }

  final class ItemRepositoryAlgebraImpl extends ItemRepositoryAlgebra[IO] {
    def findByName(name: Name): IO[Item] = ???

    def delete(itemId: ItemId): IO[Unit] = ???

    def list(category: Option[Category], orderBy: Seq[SortBy], page: Option[Page]): fs2.Stream[IO, Item] = ???
    override def create(item: Item): IO[Item] = ???
    override def update(item: Item): IO[Item] = ???

    override def get(id: ItemId): IO[Item] = id match {
      case `existingItemId` ⇒
        Item(
          name = Name("Some item name"),
          price = Money(999, "MAD"),
          category = Category("Some category"),
          createdAt = DateTime(Instant.now),
          updatedAt = DateTime(Instant.now),
          id = existingItemId.some,
        ).pure[IO]
      case _ ⇒ IO.raiseError(AppError.itemNotFound)
    }
  }

  final class EntryRepositoryAlgebraImpl extends EntryRepositoryAlgebra[IO] {
    def create(entry: Entry): IO[Entry] = entry.itemId match {
      case `existingItemId` ⇒ entry.pure[IO]
      case _                ⇒ IO.raiseError(new RuntimeException("Unexpected parameter"))
    }

    def count(id: ItemId): IO[Long] = existingItemCount.pure[IO]
  }

  trait Context extends IOExecution {
    val itemRepo = new ItemRepositoryAlgebraImpl
    val entryRepo = new EntryRepositoryAlgebraImpl

    val p = new StockService[IO](entryRepo, itemRepo)
  }
}
