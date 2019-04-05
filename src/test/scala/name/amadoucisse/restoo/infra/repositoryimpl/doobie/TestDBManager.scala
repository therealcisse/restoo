package name.amadoucisse.restoo
package infra.repositoryimpl.doobie

import cats.effect.{ ContextShift, IO }
import doobie.util.transactor.Transactor
import org.flywaydb.core.Flyway

import scala.concurrent.ExecutionContext

object TestDBManager {
  private implicit val cs: ContextShift[IO] = IO.contextShift(ExecutionContext.global)

  private def testDbUrl(dbName: String): String =
    s"jdbc:h2:mem:test_sb_$dbName;MODE=PostgreSQL;DB_CLOSE_DELAY=-1"

  private val testDbUser = "sa"
  private val testDbPass = ""

  def xa(dbName: String): Transactor[IO] =
    Transactor.fromDriverManager[IO]("org.h2.Driver", testDbUrl(dbName), testDbUser, testDbPass)

  def createTables(dbName: String): IO[Unit] =
    IO {
      val flyway = Flyway
        .configure()
        .dataSource(testDbUrl(dbName), testDbUser, testDbPass)
        .load()

      flyway.migrate()
      ()
    }

}
