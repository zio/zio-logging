package zio.logging

import zio.logging.CapturedCause.CauseToThrowable
import zio.{ Cause, URIO, ZIO }

object log {

  def apply(level: LogLevel)(line: => String): ZIO[Logging, Nothing, Unit] =
    Logging.log(level)(line)

  val context: URIO[Logging, LogContext] =
    Logging.context

  def debug(line: => String): ZIO[Logging, Nothing, Unit] =
    Logging.debug(line)

  def derive(f: LogContext => LogContext): ZIO[Logging, Nothing, Logger[String]] =
    Logging.derive(f)

  def error(line: => String): ZIO[Logging, Nothing, Unit] =
    Logging.error(line)

  def error[E: CauseToThrowable](line: => String, cause: Cause[E]): ZIO[Logging, Nothing, Unit] =
    Logging.error(line, cause)

  def info(line: => String): ZIO[Logging, Nothing, Unit] =
    Logging.info(line)

  def locally[R <: Logging, E, A1](fn: LogContext => LogContext)(zio: ZIO[R, E, A1]): ZIO[Logging with R, E, A1] =
    Logging.locally(fn)(zio)

  def locallyM[R <: Logging, E, A1](
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
