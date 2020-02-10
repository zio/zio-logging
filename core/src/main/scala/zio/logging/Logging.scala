package zio.logging

import zio.clock.{ currentDateTime, Clock }
import zio.console.{ putStrLn, Console }
import zio.{ UIO, URIO, ZIO }
import zio.Cause

trait Logging {

  def logger: Logger

}

object Logging {

  def make[R](logger: (LogContext, => String) => URIO[R, Unit]): URIO[R, Logging] =
    Logger
      .make(logger)
      .map(l =>
        new Logging {
          override def logger: Logger = l
        }
      )

  val ignore: UIO[Logging] =
    make((_, _) => ZIO.unit)

  def console(
    format: (LogContext, => String) => String
  ): URIO[Console with Clock, Logging] =
    make((context, line) =>
      for {
        date       <- currentDateTime
        level      = context.get(LogAnnotation.Level)
        loggerName = LogAnnotation.Name.render(context.get(LogAnnotation.Name))
        _          <- putStrLn(date.toString + " " + level.render + " " + loggerName + " " + format(context, line))
      } yield ()
    )

  def error(cause: Cause[Any]): ZIO[Logging, Nothing, Unit] = 
    log(LogLevel.Error)(cause.prettyPrint)

  def throwable(t: Throwable): ZIO[Logging, Nothing, Unit] = 
    log(LogLevel.Error)(t.getStackTrace().mkString("\n"))

  def log(line: => String): ZIO[Logging, Nothing, Unit] =
    ZIO.accessM[Logging](_.logger.log(line))

  def log(level: LogLevel)(line: => String): ZIO[Logging, Nothing, Unit] =
    ZIO.accessM[Logging](_.logger.log(level)(line))

  def locallyAnnotate[A, R <: Logging, E, A1](annotation: LogAnnotation[A], value: A)(
    zio: ZIO[R, E, A1]
  ): ZIO[Logging with R, E, A1] =
    ZIO.accessM[Logging with R](_.logger.locallyAnnotate[A, R, E, A1](annotation, value)(zio))

  def locally[R1 <: Logging, E, A1](f: LogContext => LogContext)(zio: ZIO[R1, E, A1]): ZIO[R1, E, A1] =
    ZIO.accessM[R1](_.logger.locally(f)(zio))

  def logger: URIO[Logging, Logger] =
    ZIO.access[Logging](_.logger)

  def logContext: URIO[Logging, LogContext] =
    ZIO.accessM[Logging](_.logger.logContext)

}
