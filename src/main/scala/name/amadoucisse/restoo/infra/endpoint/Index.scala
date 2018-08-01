package name.amadoucisse.restoo
package infra
package endpoint

import cats.effect.Effect

import org.http4s._
import org.http4s.dsl.Http4sDsl
import org.http4s.headers.Location

@SuppressWarnings(Array("org.wartremover.warts.Throw"))
class Index[F[_]: Effect] extends Http4sDsl[F] {

  implicit val uriQueryParamEncode: QueryParamEncoder[Uri] {
    def encode(value: Uri): QueryParameterValue
  } = new QueryParamEncoder[Uri] {
    override def encode(value: Uri) =
      QueryParameterValue(value.toString)
  }
  val itemsSwaggerPath: Uri =
    Uri.unsafeFromString(s"/api/${ItemEndpoints.ApiVersion}/items/swagger-spec.json")

  val service: HttpService[F] = HttpService[F] {
    case GET -> Root =>
      TemporaryRedirect(
        Location(uri("/assets/swagger-ui/3.9.3/index.html")
          .withQueryParam("url", itemsSwaggerPath)))

  }

}

object Index {
  def endpoints[F[_]: Effect]: HttpService[F] = new Index[F].service
}
