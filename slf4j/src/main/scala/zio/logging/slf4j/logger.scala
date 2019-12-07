package zio.logging.slf4j

import zio.logging.AbstractLogging
import zio.{ Cause, ZIO }

object logger extends AbstractLogging.Service[AbstractLogging[String], String] {

  override def trace(message: => String): ZIO[AbstractLogging[String], Nothing, Unit] =
    ZIO.accessM[AbstractLogging[String]](_.logging.trace(message))

  override def debug(message: => String): ZIO[AbstractLogging[String], Nothing, Unit] =
    ZIO.accessM[AbstractLogging[String]](_.logging.debug(message))

  override def info(message: => String): ZIO[AbstractLogging[String], Nothing, Unit] =
    ZIO.accessM[AbstractLogging[String]](_.logging.info(message))

  override def warning(message: => String): ZIO[AbstractLogging[String], Nothing, Unit] =
    ZIO.accessM[AbstractLogging[String]](_.logging.warning(message))

  override def error(message: => String): ZIO[AbstractLogging[String], Nothing, Unit] =
    ZIO.accessM[AbstractLogging[String]](_.logging.error(message))

  override def error(
    message: => String,
    cause: Cause[Any]
  ): ZIO[AbstractLogging[String], Nothing, Unit] =
    ZIO.accessM[AbstractLogging[String]](_.logging.error(message, cause))
}
