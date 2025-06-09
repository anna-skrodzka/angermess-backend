import akka.stream.scaladsl.SourceQueueWithComplete
import akka.http.scaladsl.model.ws.Message

import scala.collection.mutable.ListBuffer

object ClientHub:
  val clients = ListBuffer.empty[SourceQueueWithComplete[Message]]

  def broadcast(msg: Message): Unit =
    clients.foreach(_.offer(msg))

  def register(queue: SourceQueueWithComplete[Message]): Unit =
    clients += queue
