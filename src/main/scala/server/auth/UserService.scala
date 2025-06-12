package server.auth

import com.mongodb.client.model.Filters
import com.mongodb.client.MongoCollection
import mongo.MongoClientProvider
import org.bson.Document
import server.session.UserSessionStore
import org.mindrot.jbcrypt.BCrypt

import java.time.Instant
import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}
import scala.jdk.CollectionConverters.*

case class RegisterRequest(nickname: String, password: String)
case class LoginRequest(nickname: String, password: String)
case class AuthResult(token: String)

class UserService(sessionStore: UserSessionStore)(using ec: ExecutionContext):

  private val users: MongoCollection[Document] =
    MongoClientProvider.database.getCollection("users")

  def register(req: RegisterRequest): Future[Either[String, AuthResult]] = Future {
    val nick = req.nickname.trim.toLowerCase
    val existingOpt = Option(users.find(Filters.eq("nickname", nick)).first())
    if existingOpt.flatMap(doc => Option(doc.get("nickname"))).isDefined then
      Left("Nickname already taken")
    else
      val userId = UUID.randomUUID().toString
      val hash = BCrypt.hashpw(req.password, BCrypt.gensalt())
      val doc = new Document()
        .append("_id", userId)
        .append("nickname", nick)
        .append("passwordHash", hash)
        .append("createdAt", Instant.now().toString)
      users.insertOne(doc)
      val token = UUID.randomUUID().toString
      sessionStore.save(token, userId)
      Right(AuthResult(token))
  }

  def login(req: LoginRequest): Future[Either[String, AuthResult]] = Future {
    val nick = req.nickname.trim.toLowerCase
    val userOpt = Option(users.find(Filters.eq("nickname", nick)).first())
    userOpt match
      case Some(user) =>
        val hash = user.getString("passwordHash")
        if BCrypt.checkpw(req.password, hash) then
          val token = UUID.randomUUID().toString
          sessionStore.save(token, user.getString("_id"))
          Right(AuthResult(token))
        else Left("Invalid credentials")
      case None =>
        Left("Invalid credentials")
  }

  def logout(token: String): Future[Unit] = Future {
    sessionStore.remove(token)
  }

  def me(token: String): Future[Option[Document]] = Future {
    sessionStore.get(token).flatMap { userId =>
      val user = users.find(Filters.eq("_id", userId)).first()
      Option(user)
    }
  }
