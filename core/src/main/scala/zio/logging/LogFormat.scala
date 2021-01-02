package zio.logging

import zio.Cause

import scala.Console._

/**
 *  Log Format represents function that that take context with all log annotations and string line and produce final log entry.
 *
 *  Idea is that those format should be composed by decoration.
 */
trait LogFormat[A] {
  def format(context: LogContext, line: A): String
}

object LogFormat {

  private val NL = System.lineSeparator()

  type LineFormatter = (LogContext, => String) => String

  def fromFunction(fn: LineFormatter): LogFormat[String] =
    new LogFormat[String] {
      override def format(context: LogContext, line: String): String =
        fn(context, line)
    }

  final case class SimpleConsoleLogFormat(format0: LineFormatter = (_, line) => line) extends LogFormat[String] {
    override def format(context: LogContext, line: String): String = {

      val date       = context(LogAnnotation.Timestamp)
      val level      = context(LogAnnotation.Level)
      val loggerName = context(LogAnnotation.Name)
      val maybeError = context
        .get(LogAnnotation.Throwable)
        .map(throwable => CapturedCause(Cause.fail(throwable)))
        .orElse(context.get(LogAnnotation.Cause))
        .map(cause => NL + cause.cause.prettyPrint)
        .getOrElse("")
      date + " " + level + " " + loggerName + " " + format0(context, line) + " " + maybeError
    }
  }

  final case class ColoredLogFormat(lineFormat: LineFormatter = (_, line) => line) extends LogFormat[String] {
    private def withColor(color: String, s: String): String = s"$color$s$RESET"

    private def highlightLog(level: LogLevel, message: String): String = {
      val color = level match {
        case LogLevel.Error => RED
        case LogLevel.Warn  => YELLOW
        case LogLevel.Info  => CYAN
        case LogLevel.Debug => GREEN
        case LogLevel.Trace => MAGENTA
        case _              => RESET
      }
      withColor(color, message)
    }

    private def format(
      line: String,
      time: String,
      level: LogLevel,
      loggerName: String,
      maybeError: Option[String]
    ): String = {
      val logTag  = highlightLog(level, level.render)
      val logTime = withColor(BLUE, time)
      val logMsg  =
        f"$logTime $logTag%14s [${withColor(WHITE, loggerName)}] ${highlightLog(level, line)}"
      maybeError.fold(logMsg)(err => s"$logMsg$NL${highlightLog(level, err)}")
    }

    override def format(context: LogContext, line: String): String = {
      val date       = context(LogAnnotation.Timestamp)
      val level      = context.get(LogAnnotation.Level)
      val loggerName = context(LogAnnotation.Name)
      val maybeError = context
        .get(LogAnnotation.Throwable)
        .map(throwable => CapturedCause(Cause.fail(throwable)))
        .orElse(context.get(LogAnnotation.Cause))
        .map(_.cause.prettyPrint)
      format(lineFormat(context, line), date, level, loggerName, maybeError)
    }
  }
}
