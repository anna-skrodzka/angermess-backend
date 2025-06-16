package util

import org.apache.logging.log4j.{LogManager, Logger}

trait Logging:
  protected lazy val logger: Logger = LogManager.getLogger(this.getClass)