package zio

package object logging {
  def log(line: => String): ZIO[Logging, Nothing, Unit] =
    Logging.log(line)

  def log(level: LogLevel)(line: => String): ZIO[Logging, Nothing, Unit] =
    Logging.log(level)(line)

  def error(cause: Cause[Any]): ZIO[Logging, Nothing, Unit] =
    Logging.error(cause)

  def throwable(t: Throwable): ZIO[Logging, Nothing, Unit] =
    Logging.throwable(t)

  def locally[A, R <: Logging, E, A1](fn: LogContext => LogContext)(zio: ZIO[R, E, A1]): ZIO[Logging with R, E, A1] =
    Logging.locally(fn)(zio)

  def locallyAnnotate[A, R <: Logging, E, A1](annotation: LogAnnotation[A], value: A)(
    zio: ZIO[R, E, A1]
  ): ZIO[Logging with R, E, A1] =
    Logging.locallyAnnotate(annotation, value)(zio)
}
