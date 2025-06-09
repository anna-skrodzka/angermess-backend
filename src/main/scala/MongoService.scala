import MongoClientProvider.*
import org.bson.Document
import scala.jdk.CollectionConverters.*

object MongoService:
  def insert(json: String, room: String): Unit =
    try
      val doc = Document.parse(json)
      doc.put("room", room)
      messages.insertOne(doc)
    catch case e: Exception => println(s"Insert failed: ${e.getMessage}")

  def loadHistory(room: String, limit: Int = 10): List[String] =
    println(s"Loading history for room: $room")
    try
      messages.find(new Document("room", room))
        .sort(new Document("_id", -1))
        .limit(limit)
        .into(new java.util.ArrayList[Document]())
        .asScala
        .toList
        .reverse
        .map(_.toJson())
    catch
      case e: Exception =>
        println(s"History load error: ${e.getMessage}")
        List()
