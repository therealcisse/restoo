package name.amadoucisse.restoo
package http

import io.circe._
import io.circe.generic.auto._

/*
 *
 * Simple rfc6902 JSON Patch implementation
 * Supports only `Replace` operation for now
 *
 */
sealed trait JsonPatch {
  import JsonPatch._

  def path: String

  def value: Json

  final def applyOperation(json: Json): Json = this match {
    case ReplaceOp(path, value) =>
      json.mapObject { o =>
        val key = removeLeadingSlash(path)
        if (o.contains(key)) o.add(key, value) else o
      }

    case _ => json
  }
}

object JsonPatch {
  implicit def jsonDecoder: Decoder[Vector[JsonPatch]] = Decoder.decodeJson.map(fromJson)

  final case class ReplaceOp(path: String, value: Json) extends JsonPatch

  def fromJson(json: Json): Vector[JsonPatch] = {

    def toJsonPatch(v: Json) = v.hcursor.get[String]("op") match {
      case Right("replace") => v.as[ReplaceOp].toOption.toVector
      case _ => Vector.empty[JsonPatch]
    }

    if (json.isArray) json.asArray match {
      case Some(xs) => xs.flatMap(toJsonPatch)
      case _ => Vector.empty[JsonPatch]
    } else toJsonPatch(json)
  }

  private def removeLeadingSlash(path: String) = path.dropWhile(_ == '/')
}
