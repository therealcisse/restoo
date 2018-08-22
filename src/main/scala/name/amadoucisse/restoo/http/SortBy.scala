package name.amadoucisse.restoo
package http

import eu.timepit.refined.types.string.NonEmptyString

final case class SortBy(name: NonEmptyString, order: OrderBy.Order)

object OrderBy {
  sealed trait Order

  case object Ascending extends Order
  case object Descending extends Order

  private val SortByPattern = """^([\+-]?)([a-zA-Z_][a-zA-Z\d_]*)$""".r

  /*
   * @param jv comma-separated list of strings optionally prefixed with `+` or `-`
   */
  def fromString(jv: String): List[SortBy] =
    jv.split(",")
      .toList
      .collect {
        case SortByPattern("-", NonEmptyString(name)) => SortBy(name, Descending)
        case SortByPattern("+", NonEmptyString(name)) => SortBy(name, Ascending)
        case SortByPattern("", NonEmptyString(name)) => SortBy(name, Ascending)
      }

}
