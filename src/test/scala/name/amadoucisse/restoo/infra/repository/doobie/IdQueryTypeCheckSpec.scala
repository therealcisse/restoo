package name.amadoucisse.restoo
package infra
package repository.doobie

import cats.effect.IO
import common.IOAssertion
import queries.IdQueries

class IdQueryTypeCheckSpec extends RepositorySpec {
  override def testDbName: String = getClass.getSimpleName

  private lazy val repo = new DoobieIdRepositoryInterpreter[IO](transactor)

  test("Generate new item id") {
    IOAssertion {
      repo.newItemId
    }
  }

  test("Generate new entry id") {
    IOAssertion {

      repo.newEntryId
    }
  }

  test("Typecheck id queries") {
    check(IdQueries.newItemId)
    check(IdQueries.newEntryId)
  }
}
