import MongoClientProvider.*
import org.bson.Document
import scala.jdk.CollectionConverters.*

object MongoService:
  def insert(json: String): Unit =
    try
      val doc = Document.parse(json)
      messages.insertOne(doc)
      println("Saved to MongoDB")
    catch {
      case e: Exception => println(s"Mongo insert error: ${e.getMessage}")
    }

  def loadHistory(limit: Int = 10): List[String] =
    try
      messages.find()
        .sort(new Document("_id", -1))
        .limit(limit)
        .into(new java.util.ArrayList[Document]())
        .asScala
        .toList
        .reverse
        .map(_.toJson())
    catch {
      case e: Exception =>
        println(s"Error loading history: ${e.getMessage}")
        List()
    }