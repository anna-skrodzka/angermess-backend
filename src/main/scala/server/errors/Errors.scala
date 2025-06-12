package server.errors

import spray.json.DefaultJsonProtocol.jsonFormat1
import spray.json.RootJsonFormat
import spray.json.DefaultJsonProtocol.StringJsonFormat

case class ErrorResponse(error: String)

given RootJsonFormat[ErrorResponse] = jsonFormat1(ErrorResponse.apply)

object Errors:
  val malformedHeader: ErrorResponse = ErrorResponse("Malformed Authorization header")
  val missingHeader: ErrorResponse = ErrorResponse("Missing Authorization header")
  val invalidToken: ErrorResponse = ErrorResponse("Invalid or expired token")