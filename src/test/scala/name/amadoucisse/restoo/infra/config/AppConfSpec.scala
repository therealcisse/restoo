package name.amadoucisse.restoo
package infra.config

import cats.effect.IO
import config.AppConf
import org.scalatest.{ FunSuite, Matchers }

import cats.mtl.ApplicativeAsk

class AppConfSpec extends FunSuite with Matchers {

  test("load config") {

    ApplicativeAsk.askF[IO][AppConf].attempt.unsafeRunSync() shouldBe 'right
  }

}
