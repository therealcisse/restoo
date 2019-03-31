package name.amadoucisse.restoo
package http

import org.scalatest._
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks

import eu.timepit.refined.types.string.NonEmptyString
import eu.timepit.refined.auto._

class SortBySpec extends FunSuite with ScalaCheckPropertyChecks with Matchers {

  val sortByFields = Table(
    "sortByField",
    "name",
    "priceInCents",
    "category",
    "created_at",
    "updated_at",
  )

  test("some good cases") {

    forAll(sortByFields) { name ⇒
      OrderBy.fromString(NonEmptyString.unsafeFrom(name)) shouldEqual Seq(
        SortBy(NonEmptyString.unsafeFrom(name), OrderBy.Ascending)
      )

      OrderBy.fromString(NonEmptyString.unsafeFrom("+" + name)) shouldEqual Seq(
        SortBy(NonEmptyString.unsafeFrom(name), OrderBy.Ascending)
      )

      OrderBy.fromString(NonEmptyString.unsafeFrom("-" + name)) shouldEqual Seq(
        SortBy(NonEmptyString.unsafeFrom(name), OrderBy.Descending)
      )

      OrderBy.fromString(NonEmptyString.unsafeFrom("-" + name + "," + name.reverse)) shouldEqual Seq(
        SortBy(NonEmptyString.unsafeFrom(name), OrderBy.Descending),
        SortBy(NonEmptyString.unsafeFrom(name.reverse), OrderBy.Ascending)
      )

      OrderBy.fromString(NonEmptyString.unsafeFrom("-" + name + ",+" + name.reverse)) shouldEqual Seq(
        SortBy(NonEmptyString.unsafeFrom(name), OrderBy.Descending),
        SortBy(NonEmptyString.unsafeFrom(name.reverse), OrderBy.Ascending)
      )
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
