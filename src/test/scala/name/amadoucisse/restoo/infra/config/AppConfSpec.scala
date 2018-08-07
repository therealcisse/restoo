package name.amadoucisse.restoo
package infra.config

import cats.effect.IO
import config.AppConf
import org.scalatest.{FunSuite, Matchers}

class AppConfSpec extends FunSuite with Matchers {

  test("load config") {

    AppConf.load[IO].attempt.unsafeRunSync() shouldBe 'right
  }

}
