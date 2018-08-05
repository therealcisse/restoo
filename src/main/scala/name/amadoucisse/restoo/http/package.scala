package name.amadoucisse.restoo

package object http {

  case object ApiResponseCodes {

    val VALIDATION_FAILED = "VALIDATION_FAILED"

    val NOT_FOUND = "NOT_FOUND"

    val CONFLICT = "CONFLICT"
  }
}
