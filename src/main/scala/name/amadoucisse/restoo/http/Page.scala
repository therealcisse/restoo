package name.amadoucisse.restoo
package http

import java.time.Instant

final case class Page(offset: Instant, fetch: Option[Int])
