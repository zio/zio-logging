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
        level      = context.get(LogAnnotation.Level)
        loggerName = LogAnnotation.Name.render(context.get(LogAnnotation.Name))
        _          <- putStrLn(date.toString + " " + level.render + " " + loggerName + " " + format(context, line))
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
