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
    val in: Sink[Message, NotUsed] =
      Flow[Message]
        .prefixAndTail(1)
        .flatMapConcat {
          case (Seq(TextMessage.Strict(authMsg)), tail) =>
            val authData = authMsg.parseJson.asJsObject
            val tokenOpt = authData.fields.get("token").collect { case JsString(t) => t }

            tokenOpt match
              case Some(token) =>
                
                val userId = "mock-user-id"       // позже вставишь UUID из БД
                val nickname = "mock-nickname"    // или вытащишь через UserService

                tail
                  .collect {
                    case TextMessage.Strict(jsonStr) =>
                      val parsed = jsonStr.parseJson.asJsObject
                      val textOpt = parsed.fields.get("text").collect { case JsString(t) => t }
                      textOpt.map { text =>
                        JsObject(
                          "text" -> JsString(text),
                          "timestamp" -> JsString(Instant.now.toString),
                          "room" -> JsString(room),
                          "author" -> JsObject(
                            "id" -> JsString(userId),
                            "nickname" -> JsString(nickname)
                          )
                        ).compactPrint
                      }
                  }
                  .collect { case Some(enrichedJson) => enrichedJson }
                  .to(Sink.foreach { enrichedJson =>
                    MongoService.insert(enrichedJson, room)
                    ClientHub.broadcast(room, TextMessage.Strict(enrichedJson))
                  })
                  .run()

                Source.maybe

              case None =>
                println("[auth error] Invalid or missing token")
                Source.empty

          case _ =>
            println("[ws] Invalid initial message")
            Source.empty
        }
        .to(Sink.ignore)

    val history = MongoService.loadHistory(room)
    val historySource = Source(history.map(TextMessage.Strict.apply))

    val (queue, liveSource) =
      Source.queue[Message](10, OverflowStrategy.dropHead).preMaterialize()

    ClientHub.register(room, queue)

    val out = historySource.concat(liveSource)

    Flow.fromSinkAndSource(in, out)