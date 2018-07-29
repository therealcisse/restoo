package name.amadoucisse.restoo
package config

import cats.effect.Sync
import cats.implicits._
import pureconfig.error.ConfigReaderException

case class AppConfig(db: DatabaseConfig, server: ServerConfig)

object AppConfig {

  import pureconfig._

  /**
    * Loads the pet store config using PureConfig.  If configuration is invalid we will
    * return an error.  This should halt the application from starting up.
    */
  def load[F[_]](implicit E: Sync[F]): F[AppConfig] =
    E.delay(loadConfig[AppConfig]("restoo")).flatMap {
      case Right(ok) => E.pure(ok)
      case Left(e) => E.raiseError(new ConfigReaderException[AppConfig](e))
    }
}
