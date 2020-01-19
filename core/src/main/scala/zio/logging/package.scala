package zio

package object logging {
  def log(line: => String): ZIO[Logging[Any], Nothing, Unit] =
    Logging.log(line)

  def log(level: LogLevel)(line: => String): ZIO[Logging[Any], Nothing, Unit] =
    Logging.log(level)(line)

  def locallyAnnotate[A, R <: Logging[Any], E, A1](annotation: LogAnnotation[A], value: A)(
    zio: ZIO[R, E, A1]
  ): ZIO[Logging[Any] with R, E, A1] =
    Logging.locallyAnnotate(annotation, value)(zio)
}
