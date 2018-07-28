package name.amadoucisse.restoo
package utils

import cats.Monoid
import cats.data.ValidatedNel
import cats.syntax.validated._

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}

object Validator {
  final case class FieldError(name: String, message: String)

  object FieldError {
    implicit def encoder: Encoder[FieldError] = deriveEncoder
    implicit def decoder: Decoder[FieldError] = deriveDecoder
  }

  type ValidationResult[A] = ValidatedNel[FieldError, A]

  def validateNonEmpty[A](value: A, err: => FieldError)(
      implicit M: Monoid[A]): ValidationResult[A] =
    if (value != M.empty) value.validNel else err.invalidNel

  def validateNonNegative[A](value: A, err: => FieldError)(
      implicit M: Monoid[A],
      ord: Ordering[A]): ValidationResult[A] =
    if (ord.gteq(value, M.empty)) value.validNel else err.invalidNel
}
