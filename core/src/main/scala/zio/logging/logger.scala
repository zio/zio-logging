package zio.logging

import zio.{ Cause, ZIO }

object logger extends Logging.Service[Logging] {

  override def removeFromContext(key: String): ZIO[Logging, Nothing, Unit] =
    ZIO.accessM[Logging](_.logging.removeFromContext(key))

  override def addToContext(key: String, value: String): ZIO[Logging, Nothing, Unit] =
    ZIO.accessM[Logging](_.logging.addToContext(key, value))

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

  override def error(message: => String, cause: Cause[Any]): ZIO[Logging, Nothing, Unit] =
    ZIO.accessM[Logging](_.logging.error(message, cause))
}
