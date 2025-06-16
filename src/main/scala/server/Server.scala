package server

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.stream.Materializer
import org.apache.logging.log4j.LogManager

import scala.io.StdIn
import scala.util.{Failure, Success}

@main def runServer(): Unit =
  val logger = LogManager.getLogger("AngerMess")

  given system: ActorSystem = ActorSystem("AngerMess")
  given materializer: Materializer = Materializer(system)
  import system.dispatcher

  val route = Routes.allRoutes

  val bindingFuture = Http().newServerAt("localhost", 8080).bind(route)

  bindingFuture.onComplete {
    case Success(binding) =>
      val address = binding.localAddress
      logger.info(s"AngerMess is running at http://${address.getHostName}:${address.getPort}/")
      logger.info("WebSocket available at ws://localhost:8080/ws-chat")
      logger.info("Press ENTER to stop...")
    case Failure(ex) =>
      logger.error("Failed to bind HTTP server", ex)
      system.terminate()
      sys.exit(1)
  }

  StdIn.readLine()
  bindingFuture
    .flatMap(_.unbind())
    .onComplete { _ =>
      logger.info("Server is shutting down.")
      system.terminate()
    }