package zio.logging.js

import zio.clock._
import zio.logging._
import zio.{ IO, ZIO, ZLayer }

import scala.scalajs.js.Dynamic.global

object ConsoleLogger {
  private val console = global.console

  def make(
    logFormat: LogFormat[String] = LogFormat.SimpleConsoleLogFormat()
  ): ZLayer[Clock, Nothing, Logging] =
    ZLayer.requires[Clock] ++ LogAppender.make[Any, String](
      logFormat,
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
    ) >+> Logging.make >>> Logging.modifyLoggerM(Logging.addTimestamp)
}
