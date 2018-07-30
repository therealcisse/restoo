package name.amadoucisse.restoo
package infra
package repository

import cats.effect.IO
import _root_.doobie.Transactor

import config.{DatabaseConfig, AppConf}

package object doobie {
  def getTransactor : IO[Transactor[IO]] = for {
    conf <- AppConf.load[IO]
    tr <- DatabaseConfig.dbTransactor[IO](conf.db)
    x <- DatabaseConfig.initializeDb(conf.db, tr)
  } yield tr

  lazy val testTransactor : Transactor[IO] = getTransactor.unsafeRunSync()
}
