package name.amadoucisse.restoo

import cats.effect.{Effect, IO}
import fs2.{Stream, StreamApp}
import org.http4s.server.blaze.BlazeBuilder

import scala.concurrent.ExecutionContext.Implicits.global

object Server extends StreamApp[IO] {
  def stream(args: List[String], requestShutdown: IO[Unit]): Stream[IO, StreamApp.ExitCode] =
    ServerStream.stream[IO]
}

object ServerStream {

  def stream[F[_]: Effect]: Stream[F, StreamApp.ExitCode] =
    for {
      exitCode <- BlazeBuilder[F]
        .bindHttp(8080, "localhost")
        .serve
    } yield exitCode
}
