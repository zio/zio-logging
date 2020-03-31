package zio.logging

import zio.clock.{ currentDateTime, Clock }
import zio.console.{ putStrLn, Console }
import zio._

object Logging {
  type Logging = Has[Logging.Service]

  trait Service {
    def logger: Logger
  }

  def console(
    format: (LogContext, => String) => String
  ): ZLayer[Console with Clock, Nothing, Logging] =
    make((context, line) =>
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
      } yield ()
    )

  val ignore: Layer[Nothing, Logging] =
    make((_, _) => ZIO.unit)

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
}
