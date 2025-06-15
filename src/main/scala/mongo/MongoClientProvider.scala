package mongo

import com.mongodb.client.{MongoClient, MongoClients, MongoCollection, MongoDatabase}
import org.bson.Document

object MongoClientProvider:
  val client: MongoClient = MongoClients.create("mongodb://localhost:27017")
  val database: MongoDatabase = client.getDatabase("angermess")
  val messages: MongoCollection[Document] = database.getCollection("messages")
  val rooms: MongoCollection[Document] = database.getCollection("rooms")