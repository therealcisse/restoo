package name.amadoucisse.restoo
package infra
package repository.doobie

import cats.effect.IO
import domain.items.{ Category, ItemId, Name }
import http.{ OrderBy, SortBy }
import eu.timepit.refined.auto._
import Arbitraries.item
import common.IOAssertion
import queries.ItemQueries

class ItemQueryTypeCheckSpec extends RepositorySpec {
  override def testDbName: String = getClass.getSimpleName

  private lazy val repo = new DoobieItemRepositoryInterpreter[IO](transactor)

  test("NOT find by name") {
    IOAssertion {
      for {
        _ ← repo.findByName(Name("Some item"))
      } yield {
        fail()
      }
    }
  }

  test("NOT list any") {
    IOAssertion {
      for {
        _ ← repo.list(None, Nil).compile.toList
      } yield {
        fail()
      }
    }
  }

  test("NOT find by id") {
    IOAssertion {
      for {
        _ ← repo.get(ItemId(1))
      } yield {
        fail()
      }
    }
  }

  test("Always delete") {
    IOAssertion {
      for {
        _ ← repo.delete(ItemId(1))
      } yield {
        fail()
      }
    }
  }

  test("Typecheck item queries") {
    item.arbitrary.sample.map { u ⇒
      check(ItemQueries.insert(u))
      check(ItemQueries.byName(u.name))
      u.id.foreach(id ⇒ check(ItemQueries.update(u, id)))
    }
    check(ItemQueries.selectAll(None, Nil))
    check(
      ItemQueries.selectAll(
        None,
        Seq(
          SortBy("created_at", OrderBy.Descending),
          SortBy("updated_at", OrderBy.Ascending),
          SortBy("name", OrderBy.Descending)
        )
      )
    )
    check(ItemQueries.selectAll(Some(Category("category")), Nil))
    check(
      ItemQueries.selectAll(
        Some(Category("category")),
        Seq(SortBy("name", OrderBy.Descending), SortBy("category", OrderBy.Ascending))
      )
    )
    check(ItemQueries.select(ItemId(1)))
    check(ItemQueries.delete(ItemId(1)))
    check(ItemQueries.touch(ItemId(1)))
  }
}
