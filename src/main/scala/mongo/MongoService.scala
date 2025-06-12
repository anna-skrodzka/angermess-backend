package mongo

import com.mongodb.client.model.Accumulators.*
import com.mongodb.client.model.Aggregates.*
import com.mongodb.client.model.Sorts.*
import org.bson.Document
import spray.json.*
import spray.json.DefaultJsonProtocol.*

import scala.jdk.CollectionConverters.*

given roomFormat: RootJsonFormat[Map[String, String]] = mapFormat[String, String]
given roomListFormat: RootJsonFormat[List[Map[String, String]]] = listFormat(roomFormat)

object MongoService:
  def insert(json: String, room: String): Unit =
    try
      val doc = Document.parse(json)
      if !doc.containsKey("text") then
        println("Skipping insert: missing 'text'")
      else
        doc.put("room", room)
        MongoClientProvider.messages.insertOne(doc)
        println(s"Saved to MongoDB")
    catch
      case e: Exception =>
        println(s"Mongo insert error: ${e.getMessage}")

  def loadHistory(room: String): List[String] =
    MongoClientProvider.messages
      .find(new Document("room", room))
      .sort(new Document("_id", -1))
      .limit(10)
      .into(new java.util.ArrayList[Document]())
      .asScala
      .toList
      .reverse
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

