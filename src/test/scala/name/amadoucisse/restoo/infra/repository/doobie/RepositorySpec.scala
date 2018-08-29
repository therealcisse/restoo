package name.amadoucisse.restoo
package infra
package repository.doobie

import cats.effect.IO
import doobie.scalatest.IOChecker
import doobie.util.transactor.Transactor
import org.scalatest.{BeforeAndAfterAll, FunSuiteLike}

trait RepositorySpec extends FunSuiteLike with IOChecker with BeforeAndAfterAll {

  def testDbName: String

  override val transactor: Transactor[IO] = TestDBManager.xa(testDbName).unsafeRunSync()

  override def beforeAll(): Unit = {
    super.beforeAll()
    TestDBManager.createTables(testDbName).unsafeRunSync()
  }

}
