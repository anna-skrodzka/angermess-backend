import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Directives.*
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.model.headers.*
import akka.stream.Materializer
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpMethods}
import spray.json.*
import DefaultJsonProtocol.given

import scala.io.StdIn

@main def runServer(): Unit =
  given system: ActorSystem = ActorSystem("AngerMess")
  given materializer: Materializer = Materializer(system)
  import system.dispatcher

  val corsHeaders = List(
    `Access-Control-Allow-Origin`.*,
    `Access-Control-Allow-Headers`("Content-Type"),
    `Access-Control-Allow-Methods`(HttpMethods.GET, HttpMethods.POST, HttpMethods.OPTIONS)
  )

  val route: Route =
    respondWithHeaders(corsHeaders) {
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

  val bindingFuture = Http().newServerAt("localhost", 8080).bind(route)

  println("AngerMess running at ws://localhost:8080/ws-chat")
  StdIn.readLine()
  bindingFuture
    .flatMap(_.unbind())
    .onComplete(_ => system.terminate())