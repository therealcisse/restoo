package name.amadoucisse.restoo

import cats.effect.{Effect, IO}
import cats.implicits._

import fs2.{Stream, StreamApp}

import io.prometheus.client.CollectorRegistry

import config.{AppConf, DatabaseConfig}
import domain.items.ItemValidationInterpreter
import infra.endpoint.{ItemEndpoints, StockEndpoints}
import infra.repository.doobie.{DoobieEntryRepositoryInterpreter, DoobieItemRepositoryInterpreter}
import service.{ItemService, StockService}

import org.http4s.server.blaze.BlazeBuilder
import org.http4s.server.prometheus.{PrometheusExportService, PrometheusMetrics}

import io.opencensus.scala.http4s.TracingMiddleware
import io.opencensus.scala.http.ServiceData

import scala.concurrent.ExecutionContext.Implicits.global

object Server extends ServerStream[IO]

class ServerStream[F[_]: Effect] extends StreamApp[F] {

  @SuppressWarnings(Array("org.wartremover.warts.Any"))
  final def stream(args: List[String], requestShutdown: F[Unit]): Stream[F, StreamApp.ExitCode] =
    for {
      conf <- Stream.eval(AppConf.load[F])
      xa <- Stream.eval(DatabaseConfig.dbTransactor(conf.db))
      _ <- Stream.eval(DatabaseConfig.initializeDb(conf.db, xa))

      itemRepo = DoobieItemRepositoryInterpreter(xa)
      entryRepo = DoobieEntryRepositoryInterpreter(xa)
      itemValidation = ItemValidationInterpreter(itemRepo)
      itemService = ItemService(itemRepo, itemValidation)
      stockService = StockService(entryRepo, itemRepo)

      metricsRegistry <- Stream.eval(CollectorRegistry.defaultRegistry.pure[F])
      withMetrics <- Stream.eval(
        PrometheusMetrics[F](metricsRegistry, prefix = conf.namespace).pure[F])
      prometheusExportService <- Stream.eval(PrometheusExportService(metricsRegistry).pure[F])

      endpoints = ItemEndpoints.endpoints(itemService) <+> StockEndpoints.endpoints(stockService)

      service <- Stream.eval(
        withMetrics(
          TracingMiddleware.withoutSpan(
            endpoints,
            ServiceData(name = "Restoo", version = "1.0.0")
          )))

      exitCode <- BlazeBuilder[F]
        .bindHttp(conf.server.port, conf.server.address)
        .mountService(service, "/api/v1/items")
        .mountService(prometheusExportService.service)
        .serve
    } yield exitCode
}

