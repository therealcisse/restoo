package name.amadoucisse.restoo
package config

import cats.effect.Sync
import cats.implicits._
import pureconfig.error.ConfigReaderException

final case class AppConf(
    namespace: String,
    db: DatabaseConf,
    server: ServerConf,
    swagger: SwaggerConf,
)

object AppConf {

  import pureconfig._

  /**
    * Loads the pet store config using PureConfig.  If configuration is invalid we will
    * return an error.  This should halt the application from starting up.
    */
  def load[F[_]](implicit E: Sync[F]): F[AppConf] =
    E.delay(loadConfig[AppConf]("restoo")).flatMap {
      case Right(ok) => E.pure(ok)
      case Left(e) => E.raiseError(new ConfigReaderException[AppConf](e))
    }
}
