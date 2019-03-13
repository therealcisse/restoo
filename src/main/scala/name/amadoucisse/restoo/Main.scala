package name.amadoucisse.restoo

import cats.effect.{ ConcurrentEffect, ContextShift, ExitCode, IO, IOApp, Resource, Sync, Timer }
import cats.temp.par._
import cats.implicits._
import cats.Parallel
import io.prometheus.client.CollectorRegistry
import config.{ AppConf, DatabaseConf }
import domain.AppError
import infra.endpoint.{ Index, ItemEndpoints }
import infra.repository.doobie.{
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

import com.ccadllc.cedi.dtrace._
import com.ccadllc.cedi.dtrace.logging.LogEmitter

import TraceSystem._

import http.{ AppHttpErrorHandler, HttpErrorHandler, SwaggerSpec }
import eu.timepit.refined.auto._

import cats.mtl.ApplicativeAsk

object Main extends IOApp {
  import com.olegpy.meow.hierarchy.deriveMonadError

  private def traceSystem[F[_]: Sync]: F[TraceSystem[F]] = LogEmitter[F](true).map { emitter ⇒
    TraceSystem(
      data = TraceSystem.Data(
        TraceSystem.Data.Identity(
          Map(
            "application name" → "restoo",
          )
        ),
        TraceSystem.Data.Meta(
          Map(
            )
        )
      ),
      emitter = emitter,
      timer = TraceSystem.realTimeTimer[F]
    )
  }

  @SuppressWarnings(Array("org.wartremover.warts.AsInstanceOf"))
  private implicit lazy val par: Parallel[IO, IO] = Parallel[IO, IO.Par].asInstanceOf[Parallel[IO, IO]]

  final def run(args: List[String]): IO[ExitCode] =
    resource[IO].use(_ ⇒ IO.never)

  private def resource[
      F[_]: Timer: ContextShift: Par: ConcurrentEffect: ApplicativeAsk[?[_], AppConf]
  ]: Resource[F, Server[F]] = {
    implicit val H: HttpErrorHandler[F, AppError] = new AppHttpErrorHandler[F]

    implicit val headerCodec: HeaderCodec = interop.xb3.headerCodec

    for {
      implicit0(ts: TraceSystem[F]) ← Resource.liftF(traceSystem[F])

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
