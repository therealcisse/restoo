package name.amadoucisse.restoo
package config

import cats.effect.{Async, Sync}
import doobie.hikari.HikariTransactor
import org.flywaydb.core.Flyway

final case class DatabaseConf(url: String, driver: String, user: String, password: String)

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
}
