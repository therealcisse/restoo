package name.amadoucisse.restoo
package http

import java.time.Instant

import eu.timepit.refined.api.Refined
import eu.timepit.refined.numeric.LessEqual

final case class Page(marker: Option[Instant], limit: Option[Int Refined Page.Limit])

object Page {
  import eu.timepit.refined.W

  type Limit = LessEqual[W.`512`.T]
}
