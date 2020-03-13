package zio.logging

import zio.clock.{ currentDateTime, Clock }
import zio.console.{ putStrLn, Console }
import zio.{ Cause, Layer, URIO, ZIO, ZLayer }

object Logging {
  trait Service {
    def logger: Logger
  }

  def console(
    format: (LogContext, => String) => String
  ): ZLayer[Console with Clock, Nothing, Logging] =
    make((context, line) =>
      for {
        date       <- currentDateTime.orDie
        level      = context.get(LogAnnotation.Level)
        loggerName = LogAnnotation.Name.render(context.get(LogAnnotation.Name))
        _          <- putStrLn(date.toString + " " + level.render + " " + loggerName + " " + format(context, line))
      } yield ()
    )
  def error(cause: Cause[Any]): ZIO[Logging, Nothing, Unit] =
    log(LogLevel.Error)(cause.prettyPrint)

  val ignore: Layer[Nothing, Logging] =
    make((_, _) => ZIO.unit)

  def locally[R1 <: Logging, E, A1](f: LogContext => LogContext)(zio: ZIO[R1, E, A1]): ZIO[R1, E, A1] =
    ZIO.accessM(_.get.logger.locally(f)(zio))

  def log(line: => String): ZIO[Logging, Nothing, Unit] =
    ZIO.accessM[Logging](_.get.logger.log(line))

  def log(level: LogLevel)(line: => String): ZIO[Logging, Nothing, Unit] =
    ZIO.accessM[Logging](_.get.logger.log(level)(line))

  def logger: URIO[Logging, Logger] =
    ZIO.access[Logging](_.get.logger)

  def logContext: URIO[Logging, LogContext] =
    ZIO.accessM[Logging](_.get.logger.logContext)

  def make[R](logger: (LogContext, => String) => URIO[R, Unit]): ZLayer[R, Nothing, Logging] =
    ZLayer.fromEffect(
      Logger
        .make(logger)
        .map(l =>
          new Service {
            override def logger: Logger = l
          }
        )
    )

  def throwable(t: Throwable): ZIO[Logging, Nothing, Unit] =
    error(Cause.die(t))

}
