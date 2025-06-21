package server.rooms

import akka.http.scaladsl.model.{ContentTypes, HttpEntity, StatusCodes}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import mongo.{MongoService, RoomSummary}
import server.auth.UserService
import spray.json._
import spray.json.DefaultJsonProtocol.{jsonFormat4, listFormat, given}
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._

import scala.concurrent.ExecutionContext

given roomSummaryFormat: RootJsonFormat[RoomSummary] = jsonFormat4(RoomSummary.apply)
given roomListFormat: RootJsonFormat[List[RoomSummary]] = listFormat(roomSummaryFormat)

object RoomsRoutes:
  def routes(using ec: ExecutionContext, userService: UserService): Route =
    concat(
      path("rooms") {
        get {
          val summaries = MongoService.getRoomSummaries
          complete(HttpEntity(ContentTypes.`application/json`, summaries.toJson.compactPrint))
        }
      },
      path("rooms" / "search") {
        get {
          parameters("q") { query =>
            val results = MongoService.searchRooms(query)
            complete(HttpEntity(ContentTypes.`application/json`, results.toJson.compactPrint))
          }
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
                  case None => complete(StatusCodes.Unauthorized, "Invalid token")
                }
              case _ => complete(StatusCodes.BadRequest, "Missing name or token")
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
                  case None => complete(StatusCodes.Unauthorized, "Invalid token")
                }
              case _ => complete(StatusCodes.BadRequest, "Missing roomId or token")
            }
          }
        }
      }
    )