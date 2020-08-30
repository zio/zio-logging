package zio.logging.js

import java.time.OffsetDateTime
import java.util.UUID

import org.scalajs.dom.ext.Ajax
import zio.{ ZIO, ZLayer }
import zio.clock.{ currentDateTime, Clock }
import zio.logging.{ LogAnnotation, LogAppender, LogContext, LogFormat, LogLevel, Logging }

import scala.scalajs.js
import scala.scalajs.js.JSON

object HTTPLogger {

  private def sendMessage(url: String, msg: js.Object) =
    Ajax.post(url, JSON.stringify(msg), headers = Map("Content-Type" -> "application/json"))

  /**
   * Parameters:
   * <ul>
   *   <li>clientId</li>
   *   <li>log level</li>
   *   <li>logger name</li>
   *   <li>message</li>
   *   <li>cause (may be null)</li>
   * </ul>
   */
  type MessageFormatter = (OffsetDateTime, String, LogLevel, String, String, Throwable) => js.Object

  val defaultFormatter: MessageFormatter = (date, clientId, level, name, msg, cause) =>
    js.Dynamic.literal(
      date = date.toString,
      clientId = clientId,
      level = level match {
        case LogLevel.Fatal => "fatal"
        case LogLevel.Error => "error"
        case LogLevel.Warn  => "warn"
        case LogLevel.Info  => "info"
        case LogLevel.Debug => "debug"
        case LogLevel.Trace => "trace"
        case LogLevel.Off   => ""
      },
      name = name,
      msg = msg,
      cause = if (cause == null) "" else cause.toString
    )

  def makeWithName(
    url: String,
    clientId: String = UUID.randomUUID().toString,
    formatter: MessageFormatter = defaultFormatter
  )(name: String)(logFormat: (LogContext, => String) => String): ZLayer[Clock, Nothing, Logging] =
    make(url, clientId, formatter)((context, line) =>
      logFormat(context.annotate(LogAnnotation.Name, name :: Nil), line)
    )

  def make(url: String, clientId: String = UUID.randomUUID().toString, formatter: MessageFormatter = defaultFormatter)(
    logFormat: (LogContext, => String) => String
  ): ZLayer[Clock, Nothing, Logging] =
    LogAppender.make[Clock, String](
      LogFormat.fromFunction(logFormat),
      (context, line) =>
        for {
          date      <- currentDateTime.orDie
          level      = context.get(LogAnnotation.Level)
          loggerName = LogAnnotation.Name.render(context.get(LogAnnotation.Name))
          msg        = formatter(date, clientId, level, loggerName, logFormat(context, line), null)
          _         <- ZIO.effectTotal(sendMessage(url, msg))
        } yield ()
    ) >>> Logging.make

}
