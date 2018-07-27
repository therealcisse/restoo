package name.amadoucisse.restoo
package infra
package repository

import cats.effect.IO
import _root_.doobie.Transactor

import config.{DatabaseConfig, AppConfig}

package object doobie {
  def getTransactor : IO[Transactor[IO]] = for {
    conf <- AppConfig.load[IO]
    tr <- DatabaseConfig.dbTransactor[IO](conf.db)
    x <- DatabaseConfig.initializeDb(conf.db, tr)
  } yield tr

  lazy val testTransactor : Transactor[IO] = getTransactor.unsafeRunSync()
}
