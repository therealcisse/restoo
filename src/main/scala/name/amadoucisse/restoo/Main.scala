package name.amadoucisse.restoo

import cats.effect.{ ConcurrentEffect, ContextShift, ExitCode, IO, IOApp, Resource, Timer }
import cats.implicits._
import io.prometheus.client.CollectorRegistry
import config.{ AppConf, DatabaseConf }
import domain.AppError
import infra.endpoint.{ Index, ItemEndpoints }
import infra.repository.doobie.{ DoobieEntryRepositoryInterpreter, DoobieItemRepositoryInterpreter }
import doobie.util.ExecutionContexts
import service.{ ItemService, StockService }
import org.http4s.implicits._
import org.http4s.server.{ Router, Server }
import org.http4s.server.staticcontent.{ MemoryCache, WebjarService, webjarService }
import org.http4s.server.blaze.BlazeServerBuilder
import org.http4s.server.middleware.Metrics
import org.http4s.metrics.prometheus.{ Prometheus, PrometheusExportService }

//import io.opencensus.scala.http4s.TracingMiddleware
//import io.opencensus.scala.http.ServiceData
import http.{ AppHttpErrorHandler, HttpErrorHandler, SwaggerSpec }
import eu.timepit.refined.auto._

object Main extends IOApp {
  import com.olegpy.meow.hierarchy._

  final def run(args: List[String]): IO[ExitCode] =
    resource[IO].use(_ ⇒ IO.never)

  private def resource[F[_]: Timer: ContextShift](implicit F: ConcurrentEffect[F]): Resource[F, Server[F]] = {
    implicit val H: HttpErrorHandler[F, AppError] = new AppHttpErrorHandler[F]

    for {
      conf ← Resource.liftF(AppConf.load[F])

      xa ← DatabaseConf.dbTransactor(
        conf.db,
      )
      _ ← Resource.liftF(DatabaseConf.migrateDb(xa))

      itemRepo = DoobieItemRepositoryInterpreter(xa)
      entryRepo = DoobieEntryRepositoryInterpreter(xa)
      itemService = ItemService(itemRepo)
      stockService = StockService(entryRepo, itemRepo)

      endpoints = ItemEndpoints.endpoints(itemService, stockService, conf.swagger)

      registry = CollectorRegistry.defaultRegistry

      service = Metrics[F](Prometheus(registry, prefix = conf.namespace))(
        // TracingMiddleware.withoutSpan( // TODO: uncomment when opencensus is updated
        endpoints,
        //   ServiceData(name = "Restoo", version = "1.0.0")
        // )
      )

      metricsExportService = PrometheusExportService.service(registry)
      _ = PrometheusExportService.addDefaults(registry)

      assetsBlockingEC ← ExecutionContexts.cachedThreadPool[F]

      httpApp = Router(
        "/" → Index.endpoints,
        "/" → metricsExportService,
        s"/api/${SwaggerSpec.ApiVersion}/items" → service,
        "/assets" → webjarService(
          WebjarService
            .Config(
              blockingExecutionContext = assetsBlockingEC,
              cacheStrategy = MemoryCache[F]
            )
        ),
      ).orNotFound

      server ← BlazeServerBuilder[F]
        .bindHttp(conf.server.port, "0.0.0.0")
        .withHttpApp(httpApp)
        .resource

    } yield server
  }
}
