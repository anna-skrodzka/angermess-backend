package mongo

import com.mongodb.client.model.Accumulators.*
import com.mongodb.client.model.Aggregates.*
import com.mongodb.client.model.Filters
import com.mongodb.client.model.Sorts.*
import org.bson.Document
import spray.json.*
import spray.json.DefaultJsonProtocol.*

import java.time.Instant
import java.util.UUID
import scala.concurrent.Future
import scala.jdk.CollectionConverters.*
import scala.concurrent.ExecutionContext.Implicits.global

given roomFormat: RootJsonFormat[Map[String, String]] = mapFormat[String, String]
given roomListFormat: RootJsonFormat[List[Map[String, String]]] = listFormat(roomFormat)

object MongoService:
  def insert(json: String, room: String): Unit =
    try
      val doc = Document.parse(json)
      if !doc.containsKey("text") then
        println("[Mongo] Skipping insert: missing 'text'")
      else if !doc.containsKey("author") then
        println("[Mongo] Skipping insert: missing 'author'")
      else if !doc.containsKey("room") then
        println("[Mongo] Skipping insert: missing 'room'")
      else
        MongoClientProvider.messages.insertOne(doc)
        val author = doc.get("author").asInstanceOf[org.bson.Document]
        val nickname = author.getString("nickname")
        val roomName = doc.getString("room")
        println(s"[Mongo] Inserted message from $nickname in room $roomName")
    catch
      case e: Exception =>
        println(s"[Mongo insert error] ${e.getMessage}")

  def loadHistory(room: String, offset: Int = 0, limit: Int = 10): List[String] =
    MongoClientProvider.messages
      .find(new Document("room", room))
      .sort(new Document("_id", -1))
      .skip(offset)
      .limit(limit)
      .into(new java.util.ArrayList[Document]())
      .asScala
      .toList
      .map(_.toJson())

  def getRoomSummaries: List[Map[String, String]] =
    val pipeline = List(
      sort(descending("timestamp")),
      group("$room", first("last", "$text"))
    ).asJava

    val results = MongoClientProvider
      .messages
      .aggregate(pipeline)
      .asScala
      .toList

    results.map { doc =>
      Map(
        "name" -> doc.get("_id").toString,
        "last" -> doc.getString("last")
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
    true
  }.recover {
    case e =>
      println(s"[Mongo] Room creation error: ${e.getMessage}")
      false
  }

  def deleteRoom(roomId: String, requesterId: String): Future[Boolean] = Future {
    val filter = Filters.eq("_id", roomId)
    val roomOpt = Option(MongoClientProvider.rooms.find(filter).first())

    roomOpt.exists { room =>
      Option(room.getString("creatorId")).contains(requesterId) && {
        MongoClientProvider.rooms.deleteOne(filter)
        true
      }
    }
  }.recover {
    case e =>
      println(s"[Mongo] Room deletion error: ${e.getMessage}")
      false
  }

