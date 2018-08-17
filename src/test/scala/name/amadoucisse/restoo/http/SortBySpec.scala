package name.amadoucisse.restoo
package http

import org.scalatest._
import org.scalatest.prop.PropertyChecks

class OrderBySpec extends FunSuite with PropertyChecks with Matchers {

  val sortByFields = Table(
    "sortByField",
    "name",
    "price",
    "category",
    "created_at",
    "updated_at",
  )

  test("some good cases") {

    forAll(sortByFields) { name =>
      OrderBy.fromString(name) shouldEqual Seq(SortBy(name, OrderBy.Ascending))
      OrderBy.fromString("+" + name) shouldEqual Seq(SortBy(name, OrderBy.Ascending))
      OrderBy.fromString("-" + name) shouldEqual Seq(SortBy(name, OrderBy.Descending))

      OrderBy.fromString("-" + name + "," + name.reverse) shouldEqual Seq(
        SortBy(name, OrderBy.Descending),
        SortBy(name.reverse, OrderBy.Ascending))
      OrderBy.fromString("-" + name + ",+" + name.reverse) shouldEqual Seq(
        SortBy(name, OrderBy.Descending),
        SortBy(name.reverse, OrderBy.Ascending))
    }
  }

  test("some bad cases") {
    OrderBy.fromString(" ") should be('empty)
    OrderBy.fromString("") should be('empty)
    OrderBy.fromString("+") should be('empty)
    OrderBy.fromString("-") should be('empty)
    OrderBy.fromString("-++++") should be('empty)
    OrderBy.fromString("----") should be('empty)
    OrderBy.fromString("-  ") should be('empty)
    OrderBy.fromString("+  ") should be('empty)
    OrderBy.fromString("~name") should be('empty)

  }
}
