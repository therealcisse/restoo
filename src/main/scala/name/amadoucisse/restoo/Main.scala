package name.amadoucisse.restoo

import cats.effect.{ ConcurrentEffect, ContextShift, ExitCode, IO, IOApp, Resource }
import cats.implicits._
import io.prometheus.client.CollectorRegistry
import config.{ AppConf, DatabaseConf }
import domain.AppError
import infra.endpoint.{ Index, ItemEndpoints }
import infra.repository.doobie.{ DoobieEntryRepositoryInterpreter, DoobieItemRepositoryInterpreter }
import service.{ ItemService, StockService }
import org.http4s.implicits._
import org.http4s.server.{ Router, Server }
import org.http4s.server.staticcontent.{ MemoryCache, WebjarService, webjarService }
import org.http4s.server.blaze.BlazeServerBuilder
import org.http4s.server.prometheus.{ PrometheusExportService, PrometheusMetrics }
//import io.opencensus.scala.http4s.TracingMiddleware
//import io.opencensus.scala.http.ServiceData
import http.{ AppHttpErrorHandler, HttpErrorHandler, SwaggerSpec }
import eu.timepit.refined.auto._

import scala.concurrent.ExecutionContext.Implicits.global

object Main extends IOApp {
  import com.olegpy.meow.hierarchy._

  final def run(args: List[String]): IO[ExitCode] =
    resource[IO].use(_ ⇒ IO.never)

  private def resource[F[_]: ContextShift](implicit F: ConcurrentEffect[F]): Resource[F, Server[F]] = {
    implicit val H: HttpErrorHandler[F, AppError] = new AppHttpErrorHandler[F]

    for {
      conf ← Resource.liftF(AppConf.load[F])
      xa ← DatabaseConf.dbTransactor(conf.db, global, global)
      _ ← Resource.liftF(DatabaseConf.migrateDb(xa))

      itemRepo = DoobieItemRepositoryInterpreter(xa)
      entryRepo = DoobieEntryRepositoryInterpreter(xa)
      itemService = ItemService(itemRepo)
      stockService = StockService(entryRepo, itemRepo)

      metricsRegistry = CollectorRegistry.defaultRegistry
      withMetrics = PrometheusMetrics[F](metricsRegistry, prefix = conf.namespace)
      prometheusExportService = PrometheusExportService.service(metricsRegistry)
      _ ← Resource.liftF(PrometheusExportService.addDefaults(metricsRegistry))

      endpoints = ItemEndpoints.endpoints(itemService, stockService, conf.swagger)

      service ← Resource.liftF(
        withMetrics(
          //TracingMiddleware.withoutSpan(
          endpoints,
          //            ServiceData(name = "Restoo", version = "1.0.0")
          //          )
        )
      )

      httpApp = Router(
        "/" → Index.endpoints,
        s"/api/${SwaggerSpec.ApiVersion}/items" → service,
        "/" → prometheusExportService,
        "/assets" → webjarService(
          WebjarService
            .Config(blockingExecutionContext = global, cacheStrategy = MemoryCache[F])
        ),
      ).orNotFound

      server ← BlazeServerBuilder[F]
        .bindHttp(conf.server.port, "0.0.0.0")
        .withHttpApp(httpApp)
        .resource

    } yield server
  }
}
