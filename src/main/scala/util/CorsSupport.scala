package util

import akka.http.scaladsl.model.HttpMethods
import akka.http.scaladsl.model.headers.*
import akka.http.scaladsl.server.Directives.*
import akka.http.scaladsl.server.Route

object CorsSupport {
  private val corsHeaders = List(
    `Access-Control-Allow-Origin`.*,
    `Access-Control-Allow-Headers`("Content-Type"),
    `Access-Control-Allow-Methods`(HttpMethods.GET, HttpMethods.POST, HttpMethods.OPTIONS)
  )

  def withCors(inner: Route): Route =
    respondWithHeaders(corsHeaders)(inner)
}