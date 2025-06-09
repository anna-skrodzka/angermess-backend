import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Directives.*
import akka.stream.Materializer

import scala.io.StdIn
import WebSocketHandler.*

@main def runServer(): Unit =
  given system: ActorSystem = ActorSystem("AngerMess")
  given materializer: Materializer = Materializer(system)
  import system.dispatcher

  val route =
    path("ws-chat") {
      handleWebSocketMessages(websocketFlow())
    }

  val bindingFuture = Http().newServerAt("localhost", 8080).bind(route)

  println("AngerMess running at ws://localhost:8080/ws-chat")
  StdIn.readLine()
  bindingFuture
    .flatMap(_.unbind())
    .onComplete(_ => system.terminate())