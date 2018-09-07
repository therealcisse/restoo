package name.amadoucisse.restoo
package service

import cats.implicits._
import cats.effect.IO
import domain.items._
import domain.AppError
import http.SortBy
import org.scalatest.{ MustMatchers, WordSpec }
import eu.timepit.refined.auto._

class ItemServiceSpec extends WordSpec with MustMatchers {
  val newItemId = ItemId(1)

  val expectedName = Name("Item name")
  val expectedPrice = Cents.fromStandardAmount(99.9)
  val expectedCategory = Category("Category")

  val existingName = Name("Item existing name")

  "add item" when {

    "name is unique" should {
      "create item" in new Context {

        val item =
          p.createItem(
              Item(
                name = expectedName,
                priceInCents = expectedPrice,
                category = expectedCategory,
              )
            )
            .unsafeRunSync()

        item.id mustBe newItemId.some
      }
    }

    "name is not unique" should {
      "fail" in new Context {

        val item =
          p.createItem(
              Item(
                name = existingName,
                priceInCents = expectedPrice,
                category = expectedCategory,
              )
            )
            .attempt
            .unsafeRunSync()

        item mustBe 'left
      }
    }

  }

  "update item" when {

    "name is unique" should {
      "update item" in new Context {

        val item =
          p.update(
              Item(
                name = expectedName,
                priceInCents = expectedPrice,
                category = expectedCategory,
              )
            )
            .unsafeRunSync()

        item.id mustBe newItemId.some
      }
    }

    "name is not unique" should {
      "fail" in new Context {

        val item =
          p.update(
              Item(
                name = existingName,
                priceInCents = expectedPrice,
                category = expectedCategory,
              )
            )
            .attempt
            .unsafeRunSync()

        item mustBe 'left
      }
    }

  }

  final class ItemRepositoryAlgebraImpl extends ItemRepositoryAlgebra[IO] {
    def create(item: Item): IO[Item] = (item.name, item.priceInCents, item.category) match {
      case (`expectedName`, `expectedPrice`, `expectedCategory`) ⇒
        Item(
          name = expectedName,
          priceInCents = expectedPrice,
          category = expectedCategory,
          id = newItemId.some
        ).pure[IO]

      case (`existingName`, _, _) ⇒ IO.raiseError(AppError.itemAlreadyExists(item))

      case _ ⇒ IO.raiseError(new RuntimeException("Unexpected parameter"))
    }

    def update(item: Item): IO[Item] = (item.name, item.priceInCents, item.category) match {
      case (`expectedName`, `expectedPrice`, `expectedCategory`) ⇒
        Item(
          name = expectedName,
          priceInCents = expectedPrice,
          category = expectedCategory,
          id = newItemId.some
        ).pure[IO]

      case (`existingName`, _, _) ⇒ IO.raiseError(AppError.itemAlreadyExists(item))

      case _ ⇒ IO.raiseError(new RuntimeException("Unexpected parameter"))
    }

    def get(id: ItemId): IO[Item] =
      Item(
        name = expectedName,
        priceInCents = expectedPrice,
        category = expectedCategory,
        id = newItemId.some
      ).pure[IO]

    def findByName(name: Name): IO[Item] = ???

    def delete(itemId: ItemId): IO[Unit] = ???

    def list(category: Option[Category], orderBy: Seq[SortBy]): fs2.Stream[IO, Item] = ???
  }

  trait Context {
    val itemRepo = new ItemRepositoryAlgebraImpl

    val p = new ItemService[IO](itemRepo)
  }
}
