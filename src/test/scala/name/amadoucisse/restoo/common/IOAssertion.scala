package name.amadoucisse.restoo
package common

import cats.effect.IO

object IOAssertion {
  def apply[A](ioa: IO[A]): Unit = ioa.runAsync(_ => IO.unit).unsafeRunSync()
}
