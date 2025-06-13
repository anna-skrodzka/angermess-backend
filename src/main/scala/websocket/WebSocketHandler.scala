package websocket

import akka.http.scaladsl.model.ws.*
import akka.stream.scaladsl.*
import akka.stream.{Materializer, OverflowStrategy}
import akka.NotUsed
import mongo.MongoService
import spray.json.*

import scala.concurrent.ExecutionContext
import java.time.Instant

object WebSocketHandler:
  def websocketFlow(room: String)(using mat: Materializer, ec: ExecutionContext): Flow[Message, Message, Any] =
    val flow = Flow[Message]
      .prefixAndTail(1)
      .flatMapConcat {
        case (Seq(TextMessage.Strict(authMsg)), tail) =>
          val token = authMsg.parseJson.asJsObject.fields.get("token").collect {
            case JsString(value) => value
          }

          token match
            case Some(t) =>
              val userId = "mock-user-id"
              val nickname = "mock-nickname"

              tail
                .collect {
                  case TextMessage.Strict(jsonStr) =>
                    val parsed = jsonStr.parseJson.asJsObject
                    parsed.fields.get("text").collect {
                      case JsString(t) =>
                        JsObject(
                          "text" -> JsString(t),
                          "timestamp" -> JsString(Instant.now.toString),
                          "room" -> JsString(room),
                          "author" -> JsObject(
                            "id" -> JsString(userId),
                            "nickname" -> JsString(nickname)
                          )
                        ).compactPrint
                    }
                }
                .collect { case Some(json) => TextMessage.Strict(json) }

            case None =>
              println("[auth error] Invalid or missing token")
              Source.empty

        case _ =>
          println("[ws] Invalid initial message")
          Source.empty
      }

    val history = MongoService.loadHistory(room).map(TextMessage.Strict(_))
    val historySource = Source(history)

    val (queue, liveSource) = Source.queue[Message](16, OverflowStrategy.dropHead).preMaterialize()
    ClientHub.register(room, queue)

    val out = historySource.concat(liveSource)

    Flow.fromSinkAndSource(flow.to(Sink.ignore), out)