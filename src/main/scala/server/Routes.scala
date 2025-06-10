package server

import akka.actor.ActorSystem
import akka.http.scaladsl.model.*
import akka.http.scaladsl.server.Directives.*
import akka.http.scaladsl.server.Route
import akka.stream.Materializer
import mongo.MongoService
import spray.json.*
import spray.json.DefaultJsonProtocol.given
import util.CorsSupport
import websocket.WebSocketHandler

object Routes {
  def allRoutes(using system: ActorSystem, mat: Materializer): Route = CorsSupport.withCors {
    concat(
      path("ws-chat") {
        parameter("room".?) { maybeRoom =>
          val room = maybeRoom.getOrElse("default")
          handleWebSocketMessages(WebSocketHandler.websocketFlow(room))
        }
      },
      path("rooms") {
        get {
          val summaries = MongoService.getRoomSummaries
          val json = summaries.toJson.compactPrint
          complete(HttpEntity(ContentTypes.`application/json`, json))
        }
      },
      options {
        complete("OK")
      }
    )
  }
}