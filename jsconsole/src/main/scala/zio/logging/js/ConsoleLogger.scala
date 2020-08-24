package zio.logging.js

import zio.clock._
import zio.logging.{ LogAnnotation, LogAppender, LogContext, LogFormat, LogLevel, Logging }
import zio.{ IO, ZIO, ZLayer }

import scala.scalajs.js.Dynamic.global

object ConsoleLogger {
  private val console = global.console

  def makeWithName(name: String)(logFormat: (LogContext, => String) => String): ZLayer[Clock, Nothing, Logging] =
    make((context, line) => logFormat(context.annotate(LogAnnotation.Name, name :: Nil), line))

  def make(logFormat: (LogContext, => String) => String): ZLayer[Clock, Nothing, Logging] =
    ZLayer.requires[Clock] ++ LogAppender.make[Any, String](
      LogFormat.fromFunction { (context, line) =>
        val date       = context(LogAnnotation.Timestamp)
        val level      = context.get(LogAnnotation.Level)
        val loggerName = context(LogAnnotation.Name)
        (date + " " + level.render + " " + loggerName + " " + logFormat(context, line))
      },
      (context, msg) => {
        val level = context.get(LogAnnotation.Level)
        level match {
          case LogLevel.Fatal => IO.effectTotal(console.error(msg)).unit
          case LogLevel.Error => IO.effectTotal(console.error(msg)).unit
          case LogLevel.Warn  => IO.effectTotal(console.warn(msg)).unit
          case LogLevel.Info  => IO.effectTotal(console.info(msg)).unit
          case LogLevel.Debug => IO.effectTotal(console.debug(msg)).unit
          case LogLevel.Trace => IO.effectTotal(console.trace(msg)).unit
          case LogLevel.Off   => ZIO.unit
        }
      }
    ) >>> Logging.modifyM(Logging.make())(Logging.addTimestamp)

}
