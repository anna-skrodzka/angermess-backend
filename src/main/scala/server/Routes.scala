package server

import akka.actor.ActorSystem
import akka.http.scaladsl.model.*
import akka.http.scaladsl.server.Directives.*
import akka.http.scaladsl.server.Route
import akka.stream.Materializer
import mongo.MongoService
import server.auth.{AuthRoutes, UserService}
import server.session.UserSessionStore
import spray.json.*
import spray.json.DefaultJsonProtocol.given
import util.CorsSupport
import websocket.WebSocketHandler

import scala.concurrent.ExecutionContext

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
      authRoutes.routes,
      options {
        complete("OK")
      }
    )
  }
}