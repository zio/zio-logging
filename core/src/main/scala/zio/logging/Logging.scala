package zio.logging

import zio._
import zio.clock.Clock
import zio.console.Console

object Logging {

  def console(
    format: (LogContext, => String) => String = (_, s) => s,
    rootLoggerName: Option[String] = None
  ): ZLayer[Console with Clock, Nothing, Logging] =
    make(
      LogWriter.ColoredLogWriter(format),
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

  val ignore: Layer[Nothing, Logging] =
    make((_, _) => ZIO.unit)

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

  def make[R](
    logger: LogWriter[R],
    rootLoggerName: Option[String] = None
  ): ZLayer[R, Nothing, Logging] =
    ZLayer.fromEffect(
      ZIO
        .environment[R]
        .flatMap(env =>
          FiberRef
            .make(LogContext.empty)
            .tap(_.getAndUpdateSome {
              case ctx if rootLoggerName.isDefined =>
                ctx.annotate(LogAnnotation.Name, rootLoggerName.toList)
            })
            .map { ref =>
              new Logger[String] {
                def locally[R1, E, A](f: LogContext => LogContext)(zio: ZIO[R1, E, A]): ZIO[R1, E, A] =
                  ref.get.flatMap(context => ref.locally(f(context))(zio))

                def log(line: => String): UIO[Unit] =
                  ref.get.flatMap(context => logger.writeLog(context, line).provide(env))

                def logContext: UIO[LogContext] = ref.get
              }
            }
        )
    )

  def throwable(line: => String, t: Throwable): ZIO[Logging, Nothing, Unit] =
    ZIO.accessM[Logging](_.get.throwable(line, t))

  def trace(line: => String): ZIO[Logging, Nothing, Unit] =
    ZIO.accessM[Logging](_.get.trace(line))

  def warn(line: => String): ZIO[Logging, Nothing, Unit] =
    ZIO.accessM[Logging](_.get.warn(line))
}
