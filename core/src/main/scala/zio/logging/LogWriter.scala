package zio.logging

import java.time.OffsetDateTime

import zio.{ Cause, URIO }
import zio.clock.{ currentDateTime, Clock }
import zio.console.{ putStrLn, Console }
import zio.logging.LogDatetimeFormatter.humanReadableDateTimeFormatter
import zio.logging.LogLevel._

import scala.io.AnsiColor._

trait LogWriter[R] {
  def writeLog(context: LogContext, line: => String): URIO[R, Unit]
}

object LogWriter {

  private val NL = System.lineSeparator()

  type LineFormatter = (LogContext, => String) => String

  case class SimpleConsoleLogWriter(format: LineFormatter = (_, s) => s) extends LogWriter[Console with Clock] {
    override def writeLog(context: LogContext, line: => String): URIO[Console with Clock, Unit] =
      for {
        date      <- currentDateTime.orDie
        level      = context(LogAnnotation.Level)
        loggerName = context(LogAnnotation.Name)
        maybeError = context
                       .get(LogAnnotation.Throwable)
                       .map(Cause.fail)
                       .orElse(context.get(LogAnnotation.Cause))
                       .map(cause => NL + cause.prettyPrint)
                       .getOrElse("")
        _         <- putStrLn(
                       humanReadableDateTimeFormatter
                         .format(date) + " " + level + " " + loggerName + " " + format(context, line) + " " + maybeError
                     )
      } yield ()
  }

  case class ColoredLogWriter(lineFormat: LineFormatter = (_, s) => s) extends LogWriter[Console with Clock] {
    private def withColor(color: String, s: String): String = s"$color$s$RESET"

    private def highlightLog(level: LogLevel, message: String): String = {
      val color = level match {
        case Error => RED
        case Warn  => YELLOW
        case Info  => CYAN
        case Debug => GREEN
        case Trace => MAGENTA
        case _     => RESET
      }
      withColor(color, message)
    }

    private def format(
      line: => String,
      time: OffsetDateTime,
      level: LogLevel,
      loggerName: String,
      maybeError: Option[String]
    ): String = {
      val logTag  = highlightLog(level, level.render)
      val logTime = withColor(BLUE, humanReadableDateTimeFormatter.format(time))
      val logMsg  =
        f"$logTime $logTag%14s [${withColor(WHITE, loggerName)}] ${highlightLog(level, line)}"
      maybeError.fold(logMsg)(err => s"$logMsg$NL${highlightLog(level, err)}")
    }

    override def writeLog(context: LogContext, line: => String): URIO[Console with Clock, Unit] =
      for {
        date      <- currentDateTime.orDie
        level      = context.get(LogAnnotation.Level)
        loggerName = context(LogAnnotation.Name)
        maybeError = context
                       .get(LogAnnotation.Throwable)
                       .map(Cause.fail)
                       .orElse(context.get(LogAnnotation.Cause))
                       .map(_.prettyPrint)
        _         <- putStrLn(format(lineFormat(context, line), date, level, loggerName, maybeError))
      } yield ()
  }
}
