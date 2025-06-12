package server.auth

import akka.http.scaladsl.server.Directives.*
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, StatusCodes}
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport.*
import server.errors.Errors
import server.errors.given
import spray.json.DefaultJsonProtocol.StringJsonFormat
import spray.json.*

import scala.concurrent.ExecutionContext

given registerRequestFormat: RootJsonFormat[RegisterRequest] = DefaultJsonProtocol.jsonFormat2(RegisterRequest.apply)
given loginRequestFormat: RootJsonFormat[LoginRequest] = DefaultJsonProtocol.jsonFormat2(LoginRequest.apply)
given authResultFormat: RootJsonFormat[AuthResult] = DefaultJsonProtocol.jsonFormat1(AuthResult.apply)

class AuthRoutes(userService: UserService)(using ec: ExecutionContext):
  val routes: Route =
    pathPrefix("auth") {
      concat(
        path("register") {
          post {
            entity(as[RegisterRequest]) { req =>
              onSuccess(userService.register(req)) {
                case Right(result) => complete(result)
                case Left(error)   => complete(StatusCodes.BadRequest -> JsObject("error" -> JsString(error)))
              }
            }
          }
        },
        path("login") {
          post {
            entity(as[LoginRequest]) { req =>
              onSuccess(userService.login(req)) {
                case Right(result) => complete(result)
                case Left(error)   => complete(StatusCodes.Unauthorized -> JsObject("error" -> JsString(error)))
              }
            }
          }
        },
        path("logout") {
          post {
            optionalHeaderValueByName("Authorization") {
              case Some(header) if header.startsWith("Bearer ") =>
                val token = header.stripPrefix("Bearer ").trim
                onSuccess(userService.logout(token)) {
                  complete(HttpEntity(ContentTypes.`application/json`, """{"status":"ok"}"""))
                }
              case Some(_) =>
                complete(StatusCodes.BadRequest, Errors.malformedHeader)
              case None =>
                complete(StatusCodes.Unauthorized, Errors.missingHeader)
            }
          }
        } ,
        path("me") {
          get {
            optionalHeaderValueByName("Authorization") {
              case Some(header) if header.startsWith("Bearer ") =>
                val token = header.stripPrefix("Bearer ").trim
                onSuccess(userService.me(token)) {
                  case Some(user) =>
                    val nick = user.getString("nickname")
                    val createdAt = user.getString("createdAt")
                    complete(HttpEntity(ContentTypes.`application/json`,
                      s"""{"nickname":"$nick","createdAt":"$createdAt"}"""
                    ))
                  case None =>
                    complete(StatusCodes.Unauthorized, Errors.invalidToken)
                }

              case Some(_) =>
                complete(StatusCodes.BadRequest, Errors.malformedHeader)

              case None =>
                complete(StatusCodes.Unauthorized, Errors.missingHeader)
            }
          }
        }
      )
    }