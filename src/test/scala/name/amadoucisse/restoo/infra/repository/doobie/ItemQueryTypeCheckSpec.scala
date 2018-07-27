package name.amadoucisse.restoo
package infra
package repository.doobie

import org.scalatest._
import cats.effect.IO

import doobie.scalatest.IOChecker
import doobie.util.transactor.Transactor

import domain.items.ItemId

import RestooArbitraries.item

class ItemQueryTypeCheckSpec extends FunSuite with Matchers with IOChecker {
  override val transactor : Transactor[IO] = testTransactor

  import ItemSQL._

  test("Typecheck item queries") {
    item.arbitrary.sample.map { u =>
      check(insert(u))
      check(byName(u.name))
      u.id.foreach(id => check(update(u, id)))
    }
    check(selectAll)
    check(select(ItemId(1)))
    check(delete(ItemId(1)))
  }
}

