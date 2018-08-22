package name.amadoucisse.restoo
package config

import cats.effect.{Async, Sync}
import doobie.hikari._, doobie.hikari.implicits._
import org.flywaydb.core.Flyway

import eu.timepit.refined.auto._
import eu.timepit.refined.types.string._

final case class DatabaseConf(
    url: NonEmptyString,
    driver: NonEmptyString,
    user: NonEmptyString,
    password: String)

object DatabaseConf {

  def dbTransactor[F[_]: Async](dbConf: DatabaseConf): F[HikariTransactor[F]] =
    HikariTransactor
      .newHikariTransactor[F](dbConf.driver, dbConf.url, dbConf.user, dbConf.password)

  /**
   * Runs the flyway migrations against the target database
   *
   */
  def initializeDb[F[_]](xa: HikariTransactor[F])(implicit S: Sync[F]): F[Unit] =
    xa.configure { ds =>
      S.delay {
        val fw = new Flyway()
        fw.setDataSource(ds)
        fw.migrate()
        ()
      }
    }

  /**
   * Shutdown the connection pool
   *
   */
  def shutdown[F[_]: Sync](xa: HikariTransactor[F]): F[Unit] =
    xa.shutdown
}
