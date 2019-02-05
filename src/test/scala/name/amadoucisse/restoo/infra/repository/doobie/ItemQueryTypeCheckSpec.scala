package name.amadoucisse.restoo
package infra
package repository.doobie

import cats.effect.IO
import domain.items.{ Category, ItemId, Name }
import domain.{ AppError, DateTime }
import http.{ OrderBy, Page, SortBy }
import eu.timepit.refined.auto._
import Arbitraries.item
import common.IOAssertion
import org.scalatest.Matchers
import queries.ItemQueries

import java.time.Instant

class ItemQueryTypeCheckSpec extends RepositorySpec with Matchers {
  override def testDbName: String = getClass.getSimpleName

  private lazy val repo = new DoobieItemRepositoryInterpreter[IO](transactor)

  test("NOT find by name") {
    a[AppError.ItemNotFound.type] should be thrownBy IOAssertion {
      repo.findByName(Name("Some item"))
    }
  }

  test("NOT list any") {
    IOAssertion {
      for {
        items ← repo.list(None, Nil, Page(None, None)).compile.toList
      } yield {
        items shouldBe 'empty
      }
    }
  }

  test("NOT find by id") {
    a[AppError.ItemNotFound.type] should be thrownBy IOAssertion {
      repo.get(ItemId(1))
    }
  }

  test("Delete should never yield errors") {
    IOAssertion {

      repo.delete(ItemId(1)).handleErrorWith(_ ⇒ fail())
    }
  }

  test("Typecheck item queries") {
    item.arbitrary.sample.map { u ⇒
      check(ItemQueries.insert(u))
      check(ItemQueries.byName(u.name))
      check(ItemQueries.update(u))
    }
    check(ItemQueries.selectAll(None, Nil, Page(None, None)))
    check(
      ItemQueries.selectAll(
        None,
        Seq(
          SortBy("created_at", OrderBy.Descending),
          SortBy("updated_at", OrderBy.Ascending),
          SortBy("name", OrderBy.Descending)
        ),
        Page(Some(Instant.now), None),
      ),
    )
    check(ItemQueries.selectAll(Some(Category("category")), Nil, Page(None, None)))
    check(
      ItemQueries.selectAll(
        Some(Category("category")),
        Seq(SortBy("name", OrderBy.Descending), SortBy("category", OrderBy.Ascending)),
        Page(Some(Instant.now), Some(30)),
      ),
    )
    check(ItemQueries.select(ItemId(1)))
    check(ItemQueries.delete(ItemId(1)))
    check(ItemQueries.touch(ItemId(1), DateTime(Instant.now)))
  }
}
