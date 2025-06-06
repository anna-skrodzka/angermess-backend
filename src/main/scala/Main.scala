import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.model.ws._
import akka.stream.scaladsl._
import akka.stream.{Materializer, OverflowStrategy}
import akka.Done

import scala.concurrent.Future
import scala.io.StdIn
import scala.collection.mutable.ListBuffer

@main def runServer(): Unit =
  given system: ActorSystem = ActorSystem("AngerMess")
  given materializer: Materializer = Materializer(system)
  import system.dispatcher

  val clients = ListBuffer.empty[SourceQueueWithComplete[Message]]

  def websocketFlow(): Flow[Message, Message, Any] =
    val (outQueue, outSource) = Source
      .queue[Message](bufferSize = 10, OverflowStrategy.dropHead)
      .preMaterialize()

    clients += outQueue

    val in = Flow[Message].mapAsync(1) {
      case TextMessage.Strict(text) =>
        println(s"Received: $text")
        clients.foreach { q =>
          q.offer(TextMessage(s"[anon] $text"))
        }
        Future.successful(Done)

      case _ => Future.successful(Done)
    }

    Flow.fromSinkAndSource(in.to(Sink.ignore), outSource)

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