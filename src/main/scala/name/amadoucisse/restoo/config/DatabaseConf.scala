package name.amadoucisse.restoo
package config

import cats.effect.{ Async, ContextShift, Resource, Sync }
import doobie.hikari._
import org.flywaydb.core.Flyway
import eu.timepit.refined.auto._
import eu.timepit.refined.types.string._

import scala.concurrent.ExecutionContext

final case class DatabaseConf(url: NonEmptyString,
                              driverClassName: NonEmptyString,
                              user: NonEmptyString,
                              password: String)

object DatabaseConf {

  def dbTransactor[F[_]: Async: ContextShift](dbConf: DatabaseConf,
                                              connectEC: ExecutionContext,
                                              transactEC: ExecutionContext): Resource[F, HikariTransactor[F]] =
    HikariTransactor
      .newHikariTransactor[F](dbConf.driverClassName, dbConf.url, dbConf.user, dbConf.password, connectEC, transactEC)

  /**
   * Runs the flyway migrations against the target database
   *
   */
  def migrateDb[F[_]](xa: HikariTransactor[F])(implicit S: Sync[F]): F[Unit] =
    xa.configure { ds ⇒
      S.delay {
        val flyway = Flyway.configure().dataSource(ds).load()
        flyway.migrate()
        ()
      }
    }

}
