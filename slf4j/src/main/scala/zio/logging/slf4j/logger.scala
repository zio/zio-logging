package zio.logging.slf4j

import zio.logging.Logging
import zio.{ Cause, ZIO }

object logger extends Logging.Service[Logging[String], String] {

  override def trace(message: => String): ZIO[Logging[String], Nothing, Unit] =
    ZIO.accessM[Logging[String]](_.logging.trace(message))

  override def debug(message: => String): ZIO[Logging[String], Nothing, Unit] =
    ZIO.accessM[Logging[String]](_.logging.debug(message))

  override def info(message: => String): ZIO[Logging[String], Nothing, Unit] =
    ZIO.accessM[Logging[String]](_.logging.info(message))

  override def warning(message: => String): ZIO[Logging[String], Nothing, Unit] =
    ZIO.accessM[Logging[String]](_.logging.warning(message))

  override def error(message: => String): ZIO[Logging[String], Nothing, Unit] =
    ZIO.accessM[Logging[String]](_.logging.error(message))

  override def error(
    message: => String,
    cause: Cause[Any]
  ): ZIO[Logging[String], Nothing, Unit] =
    ZIO.accessM[Logging[String]](_.logging.error(message, cause))
}
