package name.amadoucisse.restoo
package config

import cats.effect.{ Async, ContextShift, Resource, Sync }
import doobie.hikari._
import doobie.util.ExecutionContexts
import org.flywaydb.core.Flyway
import eu.timepit.refined.auto._
import eu.timepit.refined.types.numeric.PosInt
import eu.timepit.refined.types.string._

import cats.mtl.ApplicativeAsk

final case class DatabaseConf(url: NonEmptyString,
                              driverClassName: NonEmptyString,
                              user: NonEmptyString,
                              password: String,
                              concurrentConnectionsFactor: PosInt)

object DatabaseConf {

  def dbTransactor[F[_]: Async: ContextShift: ApplicativeAsk[?[_], AppConf]]: Resource[F, HikariTransactor[F]] =
    for {
      dbConf ← Resource.liftF(AppConf.dbConf[F])
      size = dbConf.concurrentConnectionsFactor * Runtime.getRuntime.availableProcessors()
      connectEC ← ExecutionContexts.fixedThreadPool[F](size)
      transactEC ← ExecutionContexts.cachedThreadPool[F]
      xa ← HikariTransactor
        .newHikariTransactor[F](
          dbConf.driverClassName,
          dbConf.url,
          dbConf.user,
          dbConf.password,
          connectEC,
          transactEC,
        )
    } yield xa

  /**
   * Runs the flyway migrations against the target database
   *
   */
  def migrateDb[F[_]: Sync](xa: HikariTransactor[F]): F[Unit] =
    xa.configure { ds ⇒
      Sync[F].delay {
        val flyway = Flyway.configure().dataSource(ds).load()
        flyway.migrate()
        ()
      }
    }

}
