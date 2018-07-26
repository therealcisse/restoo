package name.amadoucisse.restoo

import config.{AppConfig, DatabaseConfig}
import cats.effect.{Effect, IO}
import fs2.{Stream, StreamApp}
import org.http4s.server.blaze.BlazeBuilder

import service.ItemService
import infra.endpoint.ItemEndpoints
import infra.repository.doobie.DoobieItemRepositoryInterpreter

import scala.concurrent.ExecutionContext.Implicits.global

object Server extends StreamApp[IO] {
  def stream(args: List[String], requestShutdown: IO[Unit]): Stream[IO, StreamApp.ExitCode] =
    ServerStream.stream[IO]
}

object ServerStream {

  def stream[F[_]: Effect]: Stream[F, StreamApp.ExitCode] =
    for {
      conf <- Stream.eval(AppConfig.load[F])
      xa <- Stream.eval(DatabaseConfig.dbTransactor(conf.db))
      _ <- Stream.eval(DatabaseConfig.initializeDb(conf.db, xa))
      itemRepo = DoobieItemRepositoryInterpreter(xa)
      itemService = ItemService(itemRepo)
      exitCode <- BlazeBuilder[F]
        .bindHttp(8080, "localhost")
        .mountService(ItemEndpoints.endpoints(itemService), "/api/v1/items")
        .serve
    } yield exitCode
}
