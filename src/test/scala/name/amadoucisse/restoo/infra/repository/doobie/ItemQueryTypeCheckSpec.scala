package name.amadoucisse.restoo
package infra
package repository.doobie

import cats.effect.IO
import domain.items.{Category, ItemId, Name}
import http.{OrderBy, SortBy}
import eu.timepit.refined.auto._
import Arbitraries.item
import common.IOAssertion

class ItemQueryTypeCheckSpec extends RepositorySpec {
  override def testDbName: String = getClass.getSimpleName

  private lazy val repo = new DoobieItemRepositoryInterpreter[IO](transactor)

  test("NOT find by name") {
    IOAssertion {
      for {
        rs <- repo.findByName(Name("Some item"))
      } yield {
        assert(rs.isEmpty)
      }
    }
  }

  test("NOT list any") {
    IOAssertion {
      for {
        rs <- repo.list(None, Nil).compile.toList
      } yield {
        assert(rs.isEmpty)
      }
    }
  }

  test("NOT find by id") {
    IOAssertion {
      for {
        rs <- repo.get(ItemId(1))
      } yield {
        assert(rs.isEmpty)
      }
    }
  }

  test("Typecheck item queries") {
    item.arbitrary.sample.map { u =>
      check(ItemSQL.insert(u))
      check(ItemSQL.byName(u.name))
      u.id.foreach(id => check(ItemSQL.update(u, id)))
    }
    check(ItemSQL.selectAll(None, Nil))
    check(
      ItemSQL.selectAll(
        None,
        Seq(
          SortBy("created_at", OrderBy.Descending),
          SortBy("updated_at", OrderBy.Ascending),
          SortBy("name", OrderBy.Descending))))
    check(ItemSQL.selectAll(Some(Category("category")), Nil))
    check(
      ItemSQL.selectAll(
        Some(Category("category")),
        Seq(SortBy("name", OrderBy.Descending), SortBy("category", OrderBy.Ascending))))
    check(ItemSQL.select(ItemId(1)))
    check(ItemSQL.delete(ItemId(1)))
    check(ItemSQL.touch(ItemId(1)))
  }
}
