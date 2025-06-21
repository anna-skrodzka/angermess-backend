package websocket

import akka.http.scaladsl.model.ws.*
import akka.stream.scaladsl.*
import akka.stream.{Materializer, OverflowStrategy}
import akka.{Done, NotUsed}
import mongo.MongoService
import server.auth.UserService
import spray.json.*
import util.Logging

import scala.concurrent.ExecutionContext
import java.time.Instant

object WebSocketHandler extends Logging:
  def websocketFlow(room: String, userService: UserService)(using mat: Materializer, ec: ExecutionContext): Flow[Message, Message, Any] =
    val in: Sink[Message, NotUsed] =
      Flow[Message]
        .prefixAndTail(1)
        .flatMapConcat {
          case (Seq(TextMessage.Strict(authMsg)), tail) =>
            val authData = authMsg.parseJson.asJsObject
            val tokenOpt = authData.fields.get("token").collect { case JsString(t) => t }

            tokenOpt match
              case Some(token) =>
                val userFuture = userService.findUserByToken(token)

                Source.future(userFuture).flatMapConcat {
                  case Some((userId, nickname)) =>
                    logger.info(s"User authenticated: $nickname ($userId) in room '$room'")
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
                      .map { enrichedJson =>
                        MongoService.insert(enrichedJson, room)
                        ClientHub.broadcast(room, TextMessage.Strict(enrichedJson))

                        val enrichedObj = enrichedJson.parseJson.asJsObject
                        val lastText = enrichedObj.fields.get("text").collect { case JsString(t) => t }.getOrElse("")
                        val authorName = enrichedObj.fields
                          .get("author")
                          .collect { case JsObject(fields) => fields.get("nickname").collect { case JsString(n) => n } }
                          .flatten
                          .getOrElse("unknown")

                        val sidebarUpdate = JsObject(
                          "type" -> JsString("room_update"),
                          "room" -> JsString(room),
                          "last" -> JsString(lastText),
                          "author" -> JsString(authorName),
                          "timestamp" -> JsNumber(Instant.now.toEpochMilli)
                        ).compactPrint

                        ClientHub.broadcast("__sidebar__", TextMessage.Strict(sidebarUpdate))
                        Done
                      }
                  case None =>
                    logger.warn(s"[WebSocket] Invalid token received: $token")
                    Source.empty
                }
              case None =>
                logger.warn("[WebSocket] Token missing in initial message")
                Source.empty
          case _ =>
            logger.warn("[WebSocket] Invalid initial WebSocket message received")
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