import akka.http.scaladsl.model.ws.*
import akka.stream.scaladsl.*
import akka.stream.{OverflowStrategy, Materializer}
import akka.{Done, NotUsed}

import scala.concurrent.Future
import MongoService.*
import ClientHub.*

object WebSocketHandler:
  def websocketFlow()(using Materializer): Flow[Message, Message, Any] = {
    val in = Flow[Message].mapAsync(1) {
      case TextMessage.Strict(json) =>
        println(s"Incoming: $json")
        insert(json)
        broadcast(TextMessage.Strict(json))
        Future.successful(Done)
      case _ => Future.successful(Done)
    }

    val history = loadHistory()
    val historySource: Source[Message, NotUsed] =
      Source(history.map(TextMessage.Strict))

    val (queue, liveSource) = Source
      .queue[Message](10, OverflowStrategy.dropHead)
      .preMaterialize()

    register(queue)

    val out = historySource.concat(liveSource)

    Flow.fromSinkAndSource(in.to(Sink.ignore), out)
  }
