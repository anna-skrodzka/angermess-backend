package mongo

import com.mongodb.client.model.Accumulators.*
import com.mongodb.client.model.Aggregates.*
import com.mongodb.client.model.{Filters, Sorts}
import com.mongodb.client.model.Sorts.*
import org.apache.logging.log4j.LogManager
import org.bson.Document
import util.Logging

import java.time.Instant
import java.util.UUID
import scala.concurrent.Future
import scala.jdk.CollectionConverters.*
import scala.concurrent.ExecutionContext.Implicits.global

case class RoomSummary(name: String, last: String, author: String)

object MongoService extends Logging:
  private val logger = LogManager.getLogger(getClass)

  def insert(json: String, room: String): Unit =
    try
      val doc = Document.parse(json)
      if !doc.containsKey("text") then
        logger.warn("Skipping insert: missing 'text' field")
      else if !doc.containsKey("author") then
        logger.warn("Skipping insert: missing 'author' field")
      else if !doc.containsKey("room") then
        logger.warn("Skipping insert: missing 'room' field")
      else
        MongoClientProvider.messages.insertOne(doc)
        val author = doc.get("author").asInstanceOf[org.bson.Document]
        val nickname = author.getString("nickname")
        val roomName = doc.getString("room")
        logger.info(s"Inserted message from '$nickname' in room '$roomName'")
    catch
      case e: Exception =>
        logger.error(s"Mongo insert error: ${e.getMessage}", e)

  def loadHistory(room: String, offset: Int = 0, limit: Int = 10): List[String] =
    MongoClientProvider.messages
      .find(new Document("room", room))
      .sort(Sorts.ascending("timestamp"))
      .skip(offset)
      .limit(limit)
      .into(new java.util.ArrayList[Document]())
      .asScala
      .toList
      .map(_.toJson())

  def getRoomSummaries: List[RoomSummary] =
    val pipeline = List(
      sort(descending("timestamp")),
      group(
        "$room",
        first("last", "$text"),
        first("author", "$author.nickname"),
        first("ts", "$timestamp")
      ),
      sort(descending("ts"))
    ).asJava

    val results = MongoClientProvider
      .messages
      .aggregate(pipeline)
      .asScala
      .toList

    results.map { doc =>
      RoomSummary(
        doc.get("_id").toString,
        doc.getString("last"),
        doc.getString("author")
      )
    }

  def createRoom(name: String, creatorId: String): Future[Boolean] = Future {
    val doc = new Document()
      .append("_id", UUID.randomUUID().toString)
      .append("name", name)
      .append("createdAt", Instant.now.toString)
      .append("creatorId", creatorId)
      .append("isPrivate", false)

    MongoClientProvider.rooms.insertOne(doc)

    val sysMsg = new Document()
      .append("_id", UUID.randomUUID().toString)
      .append("author", new Document()
        .append("id", "1")
        .append("nickname", "system"))
      .append("room", name)
      .append("text", s"new room '$name' registered")
      .append("timestamp", Instant.now.toString)

    MongoClientProvider.messages.insertOne(sysMsg)

    logger.info(s"Room '$name' created by user $creatorId")
    true
  }.recover {
    case e =>
      logger.error(s"Room creation error: ${e.getMessage}", e)
      false
  }

  def deleteRoom(roomId: String, requesterId: String): Future[Boolean] = Future {
    val filter = Filters.eq("_id", roomId)
    val roomOpt = Option(MongoClientProvider.rooms.find(filter).first())

    roomOpt.exists { room =>
      val isOwner = Option(room.getString("creatorId")).contains(requesterId)
      val roomNameOpt = Option(room.getString("name"))

      if isOwner && roomNameOpt.isDefined then
        val roomName = roomNameOpt.get

        MongoClientProvider.rooms.deleteOne(filter)

        val messageFilter = Filters.eq("room", roomName)
        val deleted = MongoClientProvider.messages.deleteMany(messageFilter)
        logger.info(s"Room $roomId deleted by user $requesterId. Messages removed: ${deleted.getDeletedCount}")

        true
      else
        logger.warn(s"Unauthorized delete attempt: user $requesterId tried to delete room $roomId")
        false
    }
  }.recover {
    case e =>
      logger.error(s"Room deletion error: ${e.getMessage}", e)
      false
  }