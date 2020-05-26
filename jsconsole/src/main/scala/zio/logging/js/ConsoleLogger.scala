package zio.logging.js

import zio.clock._
import zio.logging.Logging
import zio.logging.{ LogAnnotation, LogContext, LogLevel, Logging }
import zio.{ IO, ZIO, ZLayer }

import scala.scalajs.js.Dynamic.global

object ConsoleLogger {
  private val console = global.console

  def makeWithName(name: String)(logFormat: (LogContext, => String) => String): ZLayer[Clock, Nothing, Logging] =
    make((context, line) => logFormat(context.annotate(LogAnnotation.Name, name :: Nil), line))

  def make(logFormat: (LogContext, => String) => String): ZLayer[Clock, Nothing, Logging] =
    Logging.make { (context, line) =>
      for {
        date      <- currentDateTime.orDie
        level      = context.get(LogAnnotation.Level)
        loggerName = LogAnnotation.Name.render(context.get(LogAnnotation.Name))
        msg        = (date.toString + " " + level.render + " " + loggerName + " " + logFormat(context, line))
        _         <- level match {
                       case LogLevel.Fatal => IO.effectTotal(console.error(msg))
                       case LogLevel.Error => IO.effectTotal(console.error(msg))
                       case LogLevel.Warn  => IO.effectTotal(console.warn(msg))
                       case LogLevel.Info  => IO.effectTotal(console.info(msg))
                       case LogLevel.Debug => IO.effectTotal(console.debug(msg))
                       case LogLevel.Trace => IO.effectTotal(console.trace(msg))
                       case LogLevel.Off   => ZIO.unit
                     }
      } yield ()
    }

}
