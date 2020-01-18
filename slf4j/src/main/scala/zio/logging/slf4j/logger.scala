package zio.logging.slf4j

import zio.ZIO
import zio.logging.{LogAnnotation, LogLevel, Logger}

object logger {
  def log(line: => String): ZIO[Logger, Nothing, Unit] =
    ZIO.accessM[Logger](_.log(line))

  def log(level: LogLevel)(line: => String): ZIO[Logger, Nothing, Unit] =
    ZIO.accessM[Logger](_.log(level)(line))

  def locallyAnnotate[A, R <: Logger, E, A1](annotation: LogAnnotation[A], value: A)(zio: ZIO[R, E, A1]) =
    ZIO.accessM[Logger with R](_.locallyAnnotate[A, R, E, A1](annotation, value)(zio))
}
