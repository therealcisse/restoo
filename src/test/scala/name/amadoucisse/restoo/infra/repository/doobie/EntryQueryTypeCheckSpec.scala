package name.amadoucisse.restoo
package infra
package repository.doobie

import org.scalatest._
import cats.effect.IO

import doobie.scalatest.IOChecker
import doobie.util.transactor.Transactor

import domain.items.ItemId

import Arbitraries.entry

class EntryQueryTypeCheckSpec extends FunSuite with Matchers with IOChecker {
  override val transactor: Transactor[IO] = testTransactor

  import EntrySQL._

  test("Typecheck entry queries") {
    entry.arbitrary.sample.map { u =>
      check(insert(u))
    }
    check(count(ItemId(1)))
  }
}
