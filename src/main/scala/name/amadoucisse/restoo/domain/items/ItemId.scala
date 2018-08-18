package name.amadoucisse.restoo
package domain
package items

import io.circe._
import io.circe.generic.extras.semiauto._

import scala.util.Try

final case class ItemId(value: Int) extends AnyVal

object ItemId {
  implicit val encoder: Encoder[ItemId] = deriveUnwrappedEncoder
  implicit val decoder: Decoder[ItemId] = deriveUnwrappedDecoder

  def unapply(param: String): Option[ItemId] =
    if (param.isEmpty) None
    else Try(ItemId(param.toInt)).toOption
}
