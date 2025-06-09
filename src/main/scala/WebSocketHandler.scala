import akka.http.scaladsl.model.ws.*
import akka.stream.scaladsl.*
import akka.stream.{OverflowStrategy, Materializer}
import akka.{Done, NotUsed}

import scala.concurrent.Future

object WebSocketHandler:
  def websocketFlow(room: String)(using Materializer): Flow[Message, Message, Any] = {
    val in = Flow[Message].mapAsync(1) {
      case TextMessage.Strict(json) =>
        MongoService.insert(json, room)
        ClientHub.broadcast(room, TextMessage.Strict(json))
        Future.successful(Done)
      case _ => Future.successful(Done)
    }

    val history = MongoService.loadHistory(room)
    val historySource = Source(history.map(TextMessage.Strict.apply))

    val (queue, liveSource) = Source
      .queue[Message](10, OverflowStrategy.dropHead)
      .preMaterialize()

    ClientHub.register(room, queue)

    val out = historySource.concat(liveSource)
    Flow.fromSinkAndSource(in.to(Sink.ignore), out)
  }
