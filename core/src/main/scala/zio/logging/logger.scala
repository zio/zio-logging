package zio.logging

import zio.{ Cause, ZIO }

object logger extends Logging.Service[Logging] {

  override def trace[Message](message: => Message): ZIO[Logging with LoggingFormat[Message], Nothing, Unit] =
    ZIO.accessM[Logging with LoggingFormat[Message]](_.logging.trace(message))

  override def debug[Message](message: => Message): ZIO[Logging with LoggingFormat[Message], Nothing, Unit] =
    ZIO.accessM[Logging with LoggingFormat[Message]](_.logging.debug(message))

  override def info[Message](message: => Message): ZIO[Logging with LoggingFormat[Message], Nothing, Unit] =
    ZIO.accessM[Logging with LoggingFormat[Message]](_.logging.info(message))

  override def warning[Message](message: => Message): ZIO[Logging with LoggingFormat[Message], Nothing, Unit] =
    ZIO.accessM[Logging with LoggingFormat[Message]](_.logging.warning(message))

  override def error[Message](message: => Message): ZIO[Logging with LoggingFormat[Message], Nothing, Unit] =
    ZIO.accessM[Logging with LoggingFormat[Message]](_.logging.error(message))

  override def error[Message](message: => Message, cause: Cause[Any]): ZIO[Logging with LoggingFormat[Message], Nothing, Unit] =
    ZIO.accessM[Logging with LoggingFormat[Message]](_.logging.error(message, cause))
}