package name.amadoucisse.restoo
package config

import cats.Applicative
import cats.effect.Sync
import cats.implicits._
import eu.timepit.refined.types.string.NonEmptyString

import pureconfig.generic.auto._

import eu.timepit.refined.types.net.NonSystemPortNumber

import cats.mtl.{ ApplicativeAsk, DefaultApplicativeAsk }

final case class AppConf(
    namespace: NonEmptyString,
    db: DatabaseConf,
    server: ServerConf,
    swagger: SwaggerConf,
)

object AppConf {

  import pureconfig._
  import pureconfig.error.ConfigReaderException

  import eu.timepit.refined.pureconfig._

  def namespace[F[_]](
      implicit Cfg: ApplicativeAsk[F, AppConf]
  ): F[NonEmptyString] = Cfg.reader(_.namespace)

  def serverPort[F[_]](
      implicit Cfg: ApplicativeAsk[F, AppConf]
  ): F[NonSystemPortNumber] = Cfg.reader(_.server.port)

  def dbConf[F[_]](
      implicit Cfg: ApplicativeAsk[F, AppConf]
  ): F[DatabaseConf] = Cfg.reader(_.db)

  def swaggerConf[F[_]](
      implicit Cfg: ApplicativeAsk[F, AppConf]
  ): F[SwaggerConf] = Cfg.reader(_.swagger)

  implicit def reader[F[_]: Sync]: ApplicativeAsk[F, AppConf] =
    new DefaultApplicativeAsk[F, AppConf] {

      override val applicative: Applicative[F] =
        Applicative[F]

      override def ask: F[AppConf] =
        Sync[F].delay(loadConfig[AppConf]("restoo")).flatMap {
          case Right(ok) ⇒ Sync[F].pure(ok)
          case Left(e)   ⇒ Sync[F].raiseError(new ConfigReaderException[AppConf](e))
        }
    }
}
