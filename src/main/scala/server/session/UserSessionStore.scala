package server.session

import scala.collection.concurrent.TrieMap

class UserSessionStore:
  private val sessions = TrieMap.empty[String, String]

  def save(token: String, userId: String): Unit =
    sessions.put(token, userId)

  def getUserId(token: String): Option[String] =
    sessions.get(token)

  def remove(token: String): Unit =
    sessions.remove(token)

  def get(token: String): Option[String] =
    sessions.get(token)