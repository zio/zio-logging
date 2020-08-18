package zio.logging

import zio._
import zio.clock.Clock
import zio.console.{ putStrLn, Console }
import zio.logging.Logger.LoggerWithFormat

object Logging {

  /**
   * Add timestamp annotation to every message
   */
  def addTimestamp[A](self: Logger[A]): URIO[Clock, Logger[A]] =
    ZIO.access[Clock](env =>
      new Logger[A] {
        def locally[R1, E, A1](f: LogContext => LogContext)(zio: ZIO[R1, E, A1]): ZIO[R1, E, A1] =
          self.locally(f)(zio)

        def log(line: => A): UIO[Unit] =
          env.get.currentDateTime.orDie.flatMap(time => locally(LogAnnotation.Timestamp(time))(self.log(line)))

        def logContext: UIO[LogContext] = self.logContext
      }
    )

  def console(
    format: (LogContext, => String) => String = (_, s) => s,
    rootLoggerName: Option[String] = None
  ): ZLayer[Console with Clock, Nothing, Logging] =
    ZLayer.requires[Clock] ++ LogAppender.make[Console, String](
      LogFormat.ColoredLogFormat(format),
      (_, line) => putStrLn(line)
    ) >>> makeWithTimestamp(
      rootLoggerName
    )

  val context: URIO[Logging, LogContext] =
    ZIO.accessM[Logging](_.get.logContext)

  def debug(line: => String): ZIO[Logging, Nothing, Unit] =
    ZIO.accessM[Logging](_.get.debug(line))

  def error(line: => String): ZIO[Logging, Nothing, Unit] =
    ZIO.accessM[Logging](_.get.error(line))

  def error(line: => String, cause: Cause[Any]): ZIO[Logging, Nothing, Unit] =
    ZIO.accessM[Logging](_.get.error(line, cause))

//  val ignore: Layer[Nothing, Logging] =
//    make((_, _) => ZIO.unit)

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

  def make(rootLoggerName: Option[String] = None): URLayer[LogAppender[String], Logging] =
    ZLayer.fromFunctionM((appender: LogAppender[String]) =>
      FiberRef
        .make(LogContext.empty)
        .tap(_.getAndUpdateSome {
          case ctx if rootLoggerName.isDefined =>
            ctx.annotate(LogAnnotation.Name, rootLoggerName.toList)
        })
        .map { ref =>
          LoggerWithFormat(ref, appender.get)
        }
    )

  /**
   * creates layer with Logger that produces timestamp for every entry.
   */
  def makeWithTimestamp(rootLoggerName: Option[String] = None) =
    ZLayer.fromManaged(
      make(rootLoggerName).build
        .mapM(logger => addTimestamp(logger.get))
    )

  def throwable(line: => String, t: Throwable): ZIO[Logging, Nothing, Unit] =
    ZIO.accessM[Logging](_.get.throwable(line, t))

  def trace(line: => String): ZIO[Logging, Nothing, Unit] =
    ZIO.accessM[Logging](_.get.trace(line))

  def warn(line: => String): ZIO[Logging, Nothing, Unit] =
    ZIO.accessM[Logging](_.get.warn(line))
}
