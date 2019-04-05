package name.amadoucisse.restoo
package service

import cats.implicits._
import cats.effect.IO
import domain.{ DateTime }
import domain.items._
import domain.entries._
import repository.{ EntryRepository, IdRepository, ItemRepository }
import http.{ Page, SortBy }
import common.IOAssertion
import org.scalatest.{ MustMatchers, WordSpec }
import name.amadoucisse.restoo.domain.AppError
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks

import java.time.Instant

class StockServiceSpec extends WordSpec with MustMatchers with ScalaCheckDrivenPropertyChecks {
  val existingItemId = ItemId(1L)
  val existingItemCount = 10L

  "add stock" when {

    "item exists" should {
      "add quantity to stock" in new Context {

        val op =
          p.createEntry(existingItemId, Delta(+existingItemCount.toInt))

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

        forAll { (id: Long, delta: Int) ⇒
          whenever(id > 0L && id != existingItemId.value) {
            val op =
              p.createEntry(ItemId(id), Delta(delta))

            a[AppError.ItemNotFound.type] should be thrownBy IOAssertion {
              op
            }
          }

        }

      }
    }

  }

  final class ItemRepositoryImpl extends ItemRepository[IO] {
    def findByName(name: Name): IO[Item] = ???

    def delete(itemId: ItemId): IO[Unit] = ???

    def list(category: Option[Category], orderBy: Seq[SortBy], page: Page): IO[Vector[Item]] = ???
    def create(item: Item): IO[Unit] = ???
    def update(item: Item): IO[Unit] = ???

    def get(id: ItemId): IO[Item] = id match {
      case `existingItemId` ⇒
        Item(
          name = Name("Some item name"),
          price = Money(999, "MAD"),
          category = Category("Some category"),
          createdAt = DateTime(Instant.now),
          updatedAt = DateTime(Instant.now),
          id = existingItemId,
        ).pure[IO]
      case _ ⇒ IO.raiseError(AppError.itemNotFound)
    }
  }

  final class EntryRepositoryImpl extends EntryRepository[IO] {
    def create(entry: Entry): IO[Unit] = entry.itemId match {
      case `existingItemId` ⇒ ().pure[IO]
      case _                ⇒ IO.raiseError(new RuntimeException("Unexpected parameter"))
    }

    def count(id: ItemId): IO[Long] = existingItemCount.pure[IO]
  }

  final class IdRepositoryImpl extends IdRepository[IO] {
    def newItemId: IO[ItemId] = ???

    def newEntryId: IO[EntryId] = IO(EntryId(10L))
  }

  trait Context extends IOExecution {
    val itemRepo = new ItemRepositoryImpl
    val entryRepo = new EntryRepositoryImpl
    val idRepo = new IdRepositoryImpl

    val p = new StockService[IO](entryRepo, itemRepo, idRepo)
  }
}
