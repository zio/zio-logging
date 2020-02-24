package zio

package object logging {
  def log(line: => String): ZIO[Logging, Nothing, Unit] =
    Logging.log(line)

  def log(level: LogLevel)(line: => String): ZIO[Logging, Nothing, Unit] =
    Logging.log(level)(line)

  def logDebug(line: => String): ZIO[Logging, Nothing, Unit] =
    log(LogLevel.Debug)(line)

  def logError(cause: Cause[Any]): ZIO[Logging, Nothing, Unit] =
    Logging.error(cause)

  def logInfo(line: => String): ZIO[Logging, Nothing, Unit] =
    log(LogLevel.Info)(line)

  def logLocally[A, R <: Logging, E, A1](fn: LogContext => LogContext)(zio: ZIO[R, E, A1]): ZIO[Logging with R, E, A1] =
    Logging.locally(fn)(zio)

  def logThrowable(t: Throwable): ZIO[Logging, Nothing, Unit] =
    Logging.throwable(t)

  def logTrace(line: => String): ZIO[Logging, Nothing, Unit] =
    log(LogLevel.Trace)(line)

  def logWarn(line: => String): ZIO[Logging, Nothing, Unit] =
    log(LogLevel.Warn)(line)

}
