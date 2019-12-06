package zio.logging.slf4j

import zio.{ Cause, ZIO }

object logger extends Logging.Service[Logging] {

  override def trace(message: => String): ZIO[Logging, Nothing, Unit] =
    ZIO.accessM[Logging](_.logging.trace(message))

  override def debug(message: => String): ZIO[Logging, Nothing, Unit] =
    ZIO.accessM[Logging](_.logging.debug(message))

  override def info(message: => String): ZIO[Logging, Nothing, Unit] =
    ZIO.accessM[Logging](_.logging.info(message))

  override def warning(message: => String): ZIO[Logging, Nothing, Unit] =
    ZIO.accessM[Logging](_.logging.warning(message))

  override def error(message: => String): ZIO[Logging, Nothing, Unit] =
    ZIO.accessM[Logging](_.logging.error(message))

  override def error(
    message: => String,
    cause: Cause[Any]
  ): ZIO[Logging, Nothing, Unit] =
    ZIO.accessM[Logging](_.logging.error(message, cause))
}
