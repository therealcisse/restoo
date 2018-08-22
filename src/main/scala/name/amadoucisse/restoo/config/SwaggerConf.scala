package name.amadoucisse.restoo
package config

import eu.timepit.refined.types.string.NonEmptyString

final case class SwaggerConf(host: NonEmptyString, schemes: Seq[String])
