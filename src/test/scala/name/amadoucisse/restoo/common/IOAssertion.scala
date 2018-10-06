package name.amadoucisse.restoo
package common

import cats.effect.IO

import cats.syntax.functor._

object IOAssertion {
  def apply[A](ioa: IO[A]): Unit = ioa.void.unsafeRunSync()
}
