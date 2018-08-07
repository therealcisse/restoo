package name.amadoucisse.restoo
package infra
package repository

import cats.effect.IO
import _root_.doobie.Transactor

import name.amadoucisse.restoo.config.{AppConf, DatabaseConf}

package object doobie {
  def getTransactor: IO[Transactor[IO]] =
    for {
      conf <- AppConf.load[IO]
      xa <- DatabaseConf.dbTransactor[IO](conf.db)
      _ <- DatabaseConf.initializeDb(xa)
    } yield xa

  lazy val testTransactor: Transactor[IO] = getTransactor.unsafeRunSync()
}
