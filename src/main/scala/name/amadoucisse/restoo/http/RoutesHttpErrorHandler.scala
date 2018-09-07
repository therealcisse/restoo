package name.amadoucisse.restoo
package http

import cats.syntax.applicativeError._
import cats.syntax.functor._
import cats.ApplicativeError
import cats.data.{ Kleisli, OptionT }
import org.http4s.{ HttpRoutes, Request, Response }

object RoutesHttpErrorHandler {
  def apply[F[_], E <: Throwable](
      service: HttpRoutes[F]
  )(handler: E ⇒ F[Response[F]])(implicit ev: ApplicativeError[F, E]): HttpRoutes[F] =
    Kleisli { req: Request[F] ⇒
      OptionT {
        service.run(req).value.handleErrorWith { e ⇒
          handler(e).map(Option(_))
        }
      }
    }
}
