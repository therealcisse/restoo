package name.amadoucisse.restoo

import cats.effect.{ ConcurrentEffect, ContextShift, ExitCode, IO, IOApp, Resource, Timer }
import cats.temp.par._
import cats.implicits._
import cats.Parallel
import io.prometheus.client.CollectorRegistry
import config.{ AppConf, DatabaseConf }
import domain.AppError
import infra.endpoint.{ Index, ItemEndpoints }
import infra.repositoryimpl.doobie.{
  DoobieEntryRepositoryInterpreter,
  DoobieIdRepositoryInterpreter,
  DoobieItemRepositoryInterpreter
}
import doobie.util.ExecutionContexts
import service.{ IdService, ItemService, StockService }
import org.http4s.implicits._
import org.http4s.server.{ Router, Server }
import org.http4s.server.staticcontent.{ MemoryCache, WebjarService, webjarService }
import org.http4s.server.blaze.BlazeServerBuilder
import org.http4s.server.middleware.Metrics
import org.http4s.metrics.prometheus.{ Prometheus, PrometheusExportService }

import http.{ AppHttpErrorHandler, HttpErrorHandler, SwaggerSpec }
import eu.timepit.refined.auto._

import cats.mtl.ApplicativeAsk

object Main extends IOApp {
  import com.olegpy.meow.hierarchy.deriveMonadError

  @SuppressWarnings(Array("org.wartremover.warts.AsInstanceOf"))
  implicit lazy val par: Parallel[IO, IO] = Parallel[IO, IO.Par].asInstanceOf[Parallel[IO, IO]]

  final def run(args: List[String]): IO[ExitCode] =
    resource[IO].use(_ ⇒ IO.never)

  private def resource[
      F[_]: Timer: ContextShift: Par: ConcurrentEffect: ApplicativeAsk[?[_], AppConf]
  ]: Resource[F, Server[F]] = {
    implicit val H: HttpErrorHandler[F, AppError] = new AppHttpErrorHandler[F]

    for {
      xa ← DatabaseConf.dbTransactor[F]

      _ ← Resource.liftF(DatabaseConf.migrateDb(xa))

      idRepo = DoobieIdRepositoryInterpreter(xa)
      itemRepo = DoobieItemRepositoryInterpreter(xa)
      entryRepo = DoobieEntryRepositoryInterpreter(xa)
      idService = IdService(idRepo)
      itemService = ItemService(itemRepo)
      stockService = StockService(entryRepo, itemRepo, idRepo)

      endpoints ← Resource.liftF(ItemEndpoints.endpoints(itemService, stockService, idService))

      registry = CollectorRegistry.defaultRegistry

      namespace ← Resource.liftF(AppConf.namespace[F])

      service ← Resource.liftF {
        Prometheus[F](registry, prefix = namespace).map(Metrics[F](_)(endpoints))
      }

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

      port ← Resource.liftF(AppConf.serverPort[F])

      server ← BlazeServerBuilder[F]
        .bindHttp(port, "0.0.0.0")
        .withHttpApp(httpApp)
        .resource

    } yield server
  }
}
