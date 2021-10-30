package zio.logging.js

import zio.logging._
import zio.{ Clock, Has, IO, ZIO, ZLayer }

import scala.scalajs.js.Dynamic.global

object ConsoleLogger {
  private val console = global.console

  def make(
    logFormat: LogFormat[String] = LogFormat.SimpleConsoleLogFormat()
  ): ZLayer[Has[Clock], Nothing, Logging] =
    ZLayer.service[Clock] ++ LogAppender.make[Any, String](
      logFormat,
      (context, msg) => {
        val level = context.get(LogAnnotation.Level)
        level match {
          case LogLevel.Fatal => IO.succeed(console.error(msg)).unit
          case LogLevel.Error => IO.succeed(console.error(msg)).unit
          case LogLevel.Warn  => IO.succeed(console.warn(msg)).unit
          case LogLevel.Info  => IO.succeed(console.info(msg)).unit
          case LogLevel.Debug => IO.succeed(console.debug(msg)).unit
          case LogLevel.Trace => IO.succeed(console.trace(msg)).unit
          case LogLevel.Off   => ZIO.unit
        }
      }
    ) >+> Logging.make >>> Logging.modifyLoggerM(Logging.addTimestamp)
}
