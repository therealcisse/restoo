package name.amadoucisse.restoo
package infra
package endpoint

import cats.effect.Effect
import org.http4s._
import org.http4s.dsl.Http4sDsl
import org.http4s.headers.Location

import http.SwaggerSpec

@SuppressWarnings(Array("org.wartremover.warts.Throw"))
class Index[F[_]: Effect] extends Http4sDsl[F] {

  implicit val uriQueryParamEncode: QueryParamEncoder[Uri] =
    QueryParamEncoder[String].contramap(_.renderString)

  val itemsSwaggerPath: Uri =
    Uri.unsafeFromString(s"/api/${SwaggerSpec.ApiVersion}/items/swagger-spec.json")

  val service: HttpService[F] = HttpService[F] {
    case GET -> Root =>
      TemporaryRedirect(
        Location(uri("/assets/swagger-ui/3.17.6/index.html")
          .withQueryParam("url", itemsSwaggerPath)))

  }

}

object Index {
  def endpoints[F[_]: Effect]: HttpService[F] = new Index[F].service
}
