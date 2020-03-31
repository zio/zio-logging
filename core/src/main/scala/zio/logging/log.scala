package zio.logging

import zio.logging.Logging.Logging
import zio.{ Cause, URIO, ZIO }

object log {
  def apply(line: => String): ZIO[Logging, Nothing, Unit] =
    ZIO.accessM[Logging](_.get.log(line))

  def apply(level: LogLevel)(line: => String): ZIO[Logging, Nothing, Unit] =
    ZIO.accessM[Logging](_.get.log(level)(line))

  val context: URIO[Logging, LogContext] =
    Logging.context

  def debug(line: => String): ZIO[Logging, Nothing, Unit] =
    Logging.debug(line)

  def error(line: => String): ZIO[Logging, Nothing, Unit] =
    Logging.error(line)

  def error(line: => String, cause: Cause[Any]): ZIO[Logging, Nothing, Unit] =
    Logging.error(line, cause)

  def info(line: => String): ZIO[Logging, Nothing, Unit] =
    Logging.info(line)

  def locally[A, R <: Logging, E, A1](fn: LogContext => LogContext)(zio: ZIO[R, E, A1]): ZIO[Logging with R, E, A1] =
    Logging.locally(fn)(zio)

  def locallyM[A, R <: Logging, E, A1](
    fn: LogContext => URIO[R, LogContext]
  )(zio: ZIO[R, E, A1]): ZIO[Logging with R, E, A1] =
    Logging.locallyM(fn)(zio)

  def throwable(line: => String, t: Throwable): ZIO[Logging, Nothing, Unit] =
    Logging.throwable(line, t)

  def trace(line: => String): ZIO[Logging, Nothing, Unit] =
    Logging.trace(line)

  def warn(line: => String): ZIO[Logging, Nothing, Unit] =
    Logging.warn(line)

}
