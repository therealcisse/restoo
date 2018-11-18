package name.amadoucisse.restoo
package service

import cats.implicits._
import cats.effect.IO
import domain.items._
import domain.entries._

import http.SortBy
import org.scalatest.{ MustMatchers, WordSpec }

import eu.timepit.refined.auto._

class StockServiceSpec extends WordSpec with MustMatchers {
  val existingItemId = ItemId(1)
  val existingItemCount = 10L

  "add stock" when {

    "item exists" should {
      "add quantity to stock" in new Context {

        val stock =
          p.createEntry(existingItemId, Delta(10))
            .unsafeRunSync()

        stock.quantity mustBe existingItemCount
      }
    }

    "item does not exist" should {
      "fail" in new Context {

        val stock =
          p.createEntry(ItemId(10), Delta(50))
            .attempt
            .unsafeRunSync()

        stock mustBe 'left
      }
    }

  }

  final class ItemRepositoryAlgebraImpl extends ItemRepositoryAlgebra[IO] {
    def findByName(name: Name): IO[Item] = ???

    def delete(itemId: ItemId): IO[Unit] = ???

    def list(category: Option[Category], orderBy: Seq[SortBy]): fs2.Stream[IO, Item] = ???
    override def create(item: Item): IO[Item] = ???
    override def update(item: Item): IO[Item] = ???

    override def get(id: ItemId): IO[Item] = id match {
      case `existingItemId` ⇒
        Item(
          name = Name("Some item name"),
          price = Money(999, "MAD"),
          category = Category("Some category"),
          id = existingItemId.some
        ).pure[IO]
      case _ ⇒ IO.raiseError(new RuntimeException("Unexpected parameter"))
    }
  }

  final class EntryRepositoryAlgebraImpl extends EntryRepositoryAlgebra[IO] {
    def create(entry: Entry): IO[Entry] = entry.itemId match {
      case `existingItemId` ⇒ entry.pure[IO]
      case _                ⇒ IO.raiseError(new RuntimeException("Unexpected parameter"))
    }

    def count(id: ItemId): IO[Long] = id match {
      case `existingItemId` ⇒ existingItemCount.pure[IO]
      case _                ⇒ IO.raiseError(new RuntimeException("Unexpected parameter"))
    }
  }

  trait Context extends IOExecution {
    val itemRepo = new ItemRepositoryAlgebraImpl
    val entryRepo = new EntryRepositoryAlgebraImpl

    val p = new StockService[IO](entryRepo, itemRepo)
  }
}
