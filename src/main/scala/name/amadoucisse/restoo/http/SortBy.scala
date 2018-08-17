package name.amadoucisse.restoo
package http

final case class SortBy(name: String, order: OrderBy.Order)

object OrderBy {
  sealed trait Order

  case object Ascending extends Order
  case object Descending extends Order

  val SortByPattern = """^([\+-]?)([a-zA-Z_][a-zA-Z\d_]*)$""".r

  def fromString(jv: String): Seq[SortBy] =
    jv.split(",")
      .collect {
        case SortByPattern("-", NonEmptyString(name)) => SortBy(name, Descending)
        case SortByPattern("+", NonEmptyString(name)) => SortBy(name, Ascending)
        case SortByPattern("", NonEmptyString(name)) => SortBy(name, Ascending)
      }

}

case object NonEmptyString {
  def unapply(s: String) = if (s.isEmpty) None else Some(s)
}
