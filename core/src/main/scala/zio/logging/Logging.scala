package zio.logging

import zio._
import zio.clock._
import zio.console.Console
import zio.logging.Logger.LoggerWithFormat

object Logging {

  /**
   * Add timestamp annotation to every message
   */
  def addTimestamp[A](self: Logger[A]): URIO[Clock, Logger[A]] =
    self.deriveM(ctx => currentDateTime.orDie.map(time => LogAnnotation.Timestamp(time)(ctx)))

  def console(
    logLevel: LogLevel = LogLevel.Info,
    format: LogFormat[String] = LogFormat.ColoredLogFormat((_, s) => s),
    rootLoggerName: Option[String] = None
  ): ZLayer[Console with Clock, Nothing, Logging] =
    ZLayer.requires[Clock] ++
      LogAppender.console[String](
        logLevel,
        format
      ) >>> modifyM(
      make(
        rootLoggerName
          .map(name => LogContext.empty.annotate(LogAnnotation.Name, List(name)))
          .getOrElse(LogContext.empty)
      )
    )(addTimestamp)

  val context: URIO[Logging, LogContext] =
    ZIO.accessM[Logging](_.get.logContext)

  def debug(line: => String): ZIO[Logging, Nothing, Unit] =
    ZIO.accessM[Logging](_.get.debug(line))

  def derive(f: LogContext => LogContext): ZIO[Logging, Nothing, Logger[String]] =
    ZIO.access[Logging](_.get.derive(f))

  def error(line: => String): ZIO[Logging, Nothing, Unit] =
    ZIO.accessM[Logging](_.get.error(line))

  def error(line: => String, cause: Cause[Any]): ZIO[Logging, Nothing, Unit] =
    ZIO.accessM[Logging](_.get.error(line, cause))

  val ignore: Layer[Nothing, Logging] =
    LogAppender.ignore[String] >>> make()

  def info(line: => String): ZIO[Logging, Nothing, Unit] =
    ZIO.accessM[Logging](_.get.info(line))

  def log(level: LogLevel)(line: => String): ZIO[Logging, Nothing, Unit] =
    ZIO.accessM[Logging](_.get.log(level)(line))

  def locally[A, R <: Logging, E, A1](fn: LogContext => LogContext)(zio: ZIO[R, E, A1]): ZIO[Logging with R, E, A1] =
    ZIO.accessM(_.get.locally(fn)(zio))

  def locallyM[A, R <: Logging, E, A1](
    fn: LogContext => URIO[R, LogContext]
  )(zio: ZIO[R, E, A1]): ZIO[Logging with R, E, A1] =
    ZIO.accessM(_.get.locallyM(fn)(zio))

  def make(initialContext: LogContext = LogContext.empty): URLayer[Appender[String], Logging] =
    ZLayer.fromFunctionM((appender: Appender[String]) =>
      FiberRef
        .make(initialContext)
        .map { ref =>
          LoggerWithFormat(ref, appender.get)
        }
    )

  /**
   * modify Logger Layer.
   */
  def modifyM[R1, R2, E](
    layer: ZLayer[R1, E, Logging]
  )(fn: Logger[String] => ZIO[R2, E, Logger[String]]): ZLayer[R1 with R2, E, Logging] =
    ZLayer.fromManaged(
      layer.build
        .mapM(logger => fn(logger.get))
    )

  def throwable(line: => String, t: Throwable): ZIO[Logging, Nothing, Unit] =
    ZIO.accessM[Logging](_.get.throwable(line, t))

  def trace(line: => String): ZIO[Logging, Nothing, Unit] =
    ZIO.accessM[Logging](_.get.trace(line))

  def warn(line: => String): ZIO[Logging, Nothing, Unit] =
    ZIO.accessM[Logging](_.get.warn(line))
}
