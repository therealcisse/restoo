package name.amadoucisse.restoo
package http

import java.time.Instant

final case class Page(marker: Option[Instant], limit: Option[Int])
