package zio.logging

import zio.logging.Logging.Logging
import zio.{ Cause, URIO, ZIO }

object log {
  def apply(line: => String): ZIO[Logging, Nothing, Unit] =
    ZIO.accessM[Logging](_.get.logger.log(line))

  def apply(level: LogLevel)(line: => String): ZIO[Logging, Nothing, Unit] =
    ZIO.accessM[Logging](_.get.logger.log(level)(line))

  def context: URIO[Logging, LogContext] =
    ZIO.accessM[Logging](_.get.logger.logContext)

  def debug(line: => String): ZIO[Logging, Nothing, Unit] =
    ZIO.accessM[Logging](_.get.logger.debug(line))

  def error(line: => String): ZIO[Logging, Nothing, Unit] =
    ZIO.accessM[Logging](_.get.logger.error(line))

  def error(line: => String, cause: Cause[Any]): ZIO[Logging, Nothing, Unit] =
    ZIO.accessM[Logging](_.get.logger.error(line, cause))

  def info(line: => String): ZIO[Logging, Nothing, Unit] =
    ZIO.accessM[Logging](_.get.logger.info(line))

  def locally[A, R <: Logging, E, A1](fn: LogContext => LogContext)(zio: ZIO[R, E, A1]): ZIO[Logging with R, E, A1] =
    ZIO.accessM(_.get.logger.locally(fn)(zio))

  def locallyM[A, R <: Logging, E, A1](
    fn: LogContext => URIO[R, LogContext]
  )(zio: ZIO[R, E, A1]): ZIO[Logging with R, E, A1] =
    ZIO.accessM(_.get.logger.locallyM(fn)(zio))

  def logger: URIO[Logging, Logger] =
    ZIO.access[Logging](_.get.logger)

  def throwable(line: => String, t: Throwable): ZIO[Logging, Nothing, Unit] =
    ZIO.accessM[Logging](_.get.logger.throwable(line, t))

  def trace(line: => String): ZIO[Logging, Nothing, Unit] =
    ZIO.accessM[Logging](_.get.logger.trace(line))

  def warn(line: => String): ZIO[Logging, Nothing, Unit] =
    ZIO.accessM[Logging](_.get.logger.warn(line))

}
