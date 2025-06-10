package websocket

import akka.http.scaladsl.model.ws.Message
import akka.stream.scaladsl.SourceQueueWithComplete

import scala.collection.mutable

object ClientHub:
  private val roomClients = mutable.Map.empty[String, mutable.ListBuffer[SourceQueueWithComplete[Message]]]

  def broadcast(room: String, msg: Message): Unit =
    roomClients.getOrElse(room, mutable.ListBuffer()).foreach(_.offer(msg))

  def register(room: String, queue: SourceQueueWithComplete[Message]): Unit =
    val list = roomClients.getOrElseUpdate(room, mutable.ListBuffer())
    list += queue
