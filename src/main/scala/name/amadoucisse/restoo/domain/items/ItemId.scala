package name.amadoucisse.restoo
package domain
package items

import io.circe._
import io.circe.generic.extras.semiauto._

import scala.util.Try

final case class ItemId(value: Long) extends AnyVal

object ItemId {
  implicit val jsonEncoder: Encoder[ItemId] = deriveUnwrappedEncoder
  implicit val jsonDecoder: Decoder[ItemId] = deriveUnwrappedDecoder

  def unapply(param: String): Option[ItemId] =
    if (param.isEmpty) None
    else Try(ItemId(param.toLong)).toOption
}
