package name.amadoucisse.restoo
package infra
package repository.doobie

import cats.effect.IO
import doobie.h2.H2Transactor
import doobie.util.transactor.Transactor
import org.flywaydb.core.Flyway

object TestDBManager {

  private def testDbUrl(dbName: String): String =
    s"jdbc:h2:mem:test_sb_$dbName;MODE=PostgreSQL;DB_CLOSE_DELAY=-1"

  private val testDbUser = "sa"
  private val testDbPass = ""

  def xa(dbName: String): IO[Transactor[IO]] =
    H2Transactor.newH2Transactor[IO](testDbUrl(dbName), testDbUser, testDbPass)

  def createTables(dbName: String): IO[Unit] =
    IO {
      val flyway = new Flyway
      flyway.setDataSource(testDbUrl(dbName), testDbUser, testDbPass)
      flyway.migrate()
      ()
    }

}
