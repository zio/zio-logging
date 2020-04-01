package zio.logging

import zio._
import zio.clock.{ currentDateTime, Clock }
import zio.console.{ putStrLn, Console }

object Logging {
  type Logging = Has[Logger[String]]

  def console(
    format: (LogContext, => String) => String,
    rootLoggerName: Option[String] = None
  ): ZLayer[Console with Clock, Nothing, Logging] =
    make(
      (context, line) =>
        for {
          date       <- currentDateTime.orDie
          level      = context(LogAnnotation.Level)
          loggerName = context(LogAnnotation.Name)
          maybeError = context
            .get(LogAnnotation.Throwable)
            .map(Cause.fail)
            .orElse(context.get(LogAnnotation.Cause))
            .map(cause => System.lineSeparator() + cause.prettyPrint)
            .getOrElse("")
          _ <- putStrLn(date.toString + " " + level + " " + loggerName + " " + format(context, line) + " " + maybeError)
        } yield (),
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
    logger: (LogContext, => String) => URIO[R, Unit],
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

                def log(line: => String): UIO[Unit] = ref.get.flatMap(context => logger(context, line).provide(env))

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
