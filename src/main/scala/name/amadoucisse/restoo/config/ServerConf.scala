package name.amadoucisse.restoo
package config

import eu.timepit.refined.types.net.NonSystemPortNumber

final case class ServerConf(
    port: NonSystemPortNumber
)
