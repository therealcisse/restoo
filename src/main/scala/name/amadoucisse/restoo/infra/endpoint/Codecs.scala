package name.amadoucisse.restoo
package infra
package endpoint

import cats.effect.Sync

trait Codecs {

  import io.circe.{ Decoder, Encoder }
  import io.circe.generic.extras.decoding.UnwrappedDecoder
  import io.circe.generic.extras.encoding.UnwrappedEncoder
  import org.http4s.{ EntityDecoder, EntityEncoder }
  import org.http4s.circe.{ jsonEncoderOf, jsonOf }

  protected final implicit def valueClassEncoder[A: UnwrappedEncoder]: Encoder[A] = implicitly
  protected final implicit def valueClassDecoder[A: UnwrappedDecoder]: Decoder[A] = implicitly

  protected final implicit def jsonDecoder[F[_]: Sync, A <: Product: Decoder]: EntityDecoder[F, A] = jsonOf[F, A]
  protected final implicit def jsonEncoder[F[_]: Sync, A <: Product: Encoder]: EntityEncoder[F, A] = jsonEncoderOf[F, A]
}
