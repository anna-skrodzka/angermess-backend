package server

import akka.actor.ActorSystem
import akka.http.scaladsl.model.*
import akka.http.scaladsl.server.Directives.*
import akka.http.scaladsl.server.Route
import akka.stream.Materializer
import mongo.{MongoService, RoomSummary}
import server.auth.{AuthRoutes, UserService}
import server.session.UserSessionStore
import spray.json.*
import spray.json.DefaultJsonProtocol.{jsonFormat3, listFormat, given}
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport.*
import util.CorsSupport
import websocket.WebSocketHandler

import scala.concurrent.ExecutionContext

given roomSummaryFormat: RootJsonFormat[RoomSummary] = jsonFormat3(RoomSummary.apply)
given roomListFormat: RootJsonFormat[List[RoomSummary]] = listFormat(roomSummaryFormat)

object Routes {

  def allRoutes(using system: ActorSystem, mat: Materializer, ec: ExecutionContext): Route = CorsSupport.withCors {
    val sessionStore = new UserSessionStore()
    val userService = new UserService(sessionStore)(using ec)
    val authRoutes = new AuthRoutes(userService)(using ec)
    
    concat(
      path("ws-chat") {
        parameter("room".?) { maybeRoom =>
          val room = maybeRoom.getOrElse("default")
          handleWebSocketMessages(WebSocketHandler.websocketFlow(room, userService))
        }
      },
      path("history") {
        parameters("room", "offset".as[Int].?, "limit".as[Int].?) { (room, offsetOpt, limitOpt) =>
          get {
            val offset = offsetOpt.getOrElse(0)
            val limit = limitOpt.getOrElse(10)
            val history = MongoService.loadHistory(room, offset, limit)
            complete(HttpEntity(ContentTypes.`application/json`, history.toJson.compactPrint))
          }
        }
      },
      path("rooms") {
        get {
          val summaries = MongoService.getRoomSummaries
          val json = summaries.toJson.compactPrint
          complete(HttpEntity(ContentTypes.`application/json`, json))
        }
      },
      path("rooms" / "create") {
        post {
          entity(as[JsObject]) { json =>
            val maybeName = json.fields.get("name").collect { case JsString(s) => s }
            val maybeToken = json.fields.get("token").collect { case JsString(s) => s }

            (maybeName, maybeToken) match {
              case (Some(name), Some(token)) =>
                onSuccess(userService.findUserByToken(token)) {
                  case Some((userId, _)) =>
                    onSuccess(MongoService.createRoom(name, userId)) {
                      case true  => complete(StatusCodes.OK)
                      case false => complete(StatusCodes.InternalServerError, "Failed to create room")
                    }
                  case None =>
                    complete(StatusCodes.Unauthorized, "Invalid token")
                }
              case _ =>
                complete(StatusCodes.BadRequest, "Missing name or token")
            }
          }
        }
      },
      path("rooms" / "delete") {
        post {
          entity(as[JsObject]) { json =>
            val maybeRoomId = json.fields.get("roomId").collect { case JsString(s) => s }
            val maybeToken  = json.fields.get("token").collect { case JsString(s) => s }

            (maybeRoomId, maybeToken) match {
              case (Some(roomId), Some(token)) =>
                onSuccess(userService.findUserByToken(token)) {
                  case Some((userId, _)) =>
                    onSuccess(MongoService.deleteRoom(roomId, userId)) {
                      case true  => complete(StatusCodes.OK)
                      case false => complete(StatusCodes.Forbidden, "Cannot delete room")
                    }
                  case None =>
                    complete(StatusCodes.Unauthorized, "Invalid token")
                }
              case _ =>
                complete(StatusCodes.BadRequest, "Missing roomId or token")
            }
          }
        }
      },
      authRoutes.routes,
      options {
        complete("OK")
      }
    )
  }
}