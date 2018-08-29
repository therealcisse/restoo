package name.amadoucisse.restoo
package infra
package repository.doobie

import cats.effect.IO
import domain.items.ItemId
import Arbitraries.entry
import common.IOAssertion

class EntryQueryTypeCheckSpec extends RepositorySpec {
  override def testDbName: String = getClass.getSimpleName

  private lazy val repo = new DoobieEntryRepositoryInterpreter[IO](transactor)

  test("NOT count") {
    IOAssertion {
      for {
        rs <- repo.count(ItemId(1))
      } yield {
        assert(rs === Some(0L))
      }
    }
  }

  test("Typecheck entry queries") {
    entry.arbitrary.sample.map { u =>
      check(EntrySQL.insert(u))
    }
    check(EntrySQL.count(ItemId(1)))
  }
}
