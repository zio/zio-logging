package zio.logging

import zio.Cause

import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
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
        .map(Cause.fail)
        .orElse(context.get(LogAnnotation.Cause))
        .map(cause => NL + cause.prettyPrint)
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
        .map(Cause.fail)
        .orElse(context.get(LogAnnotation.Cause))
        .map(_.prettyPrint)
      format(lineFormat(context, line), date, level, loggerName, maybeError)
    }
  }

  object AssembledLogFormat {
    case class FormatterFunction(private[logging] val fun: (StringBuilder, LogContext, String) => Any) {
      def +(other: FormatterFunction): FormatterFunction =
        FormatterFunction { (builder, ctx, line) =>
          fun(builder, ctx, line)
          other.fun(builder, ctx, line)
        }

      def <+>(other: FormatterFunction): FormatterFunction =
        FormatterFunction { (builder, ctx, line) =>
          fun(builder, ctx, line)
          builder.append(' ')
          other.fun(builder, ctx, line)
        }

      def concat(other: FormatterFunction): FormatterFunction =
        this + other

      def spaced(other: FormatterFunction): FormatterFunction =
        this <+> other
    }

    def apply(f: FormatterFunction): AssembledLogFormat =
      new AssembledLogFormat(f)

    object DSL {
      val space: FormatterFunction        = FormatterFunction { (builder, _, _) =>
        builder.append(' ')
      }
      val bracketStart: FormatterFunction = FormatterFunction { (builder, _, _) =>
        builder.append('[')
      }
      val bracketEnd: FormatterFunction   = FormatterFunction { (builder, _, _) =>
        builder.append(']')
      }

      def renderedAnnotation[A](annotation: LogAnnotation[A]): FormatterFunction =
        FormatterFunction { (builder, ctx, _) =>
          builder.append(ctx(annotation))
        }

      def renderedAnnotationF[A](annotation: LogAnnotation[A], f: String => String): FormatterFunction =
        FormatterFunction { (builder, ctx, _) =>
          builder.append(f(ctx(annotation)))
        }

      def annotationF[A](annotation: LogAnnotation[A], f: A => String): FormatterFunction =
        FormatterFunction { (builder, ctx, _) =>
          builder.append(f(ctx.get(annotation)))
        }

      def bracketed(inner: FormatterFunction): FormatterFunction =
        bracketStart + inner + bracketEnd

      def level: FormatterFunction =
        renderedAnnotation(LogAnnotation.Level)

      def LEVEL: FormatterFunction =
        renderedAnnotationF(LogAnnotation.Level, _.toUpperCase)

      def name: FormatterFunction =
        renderedAnnotation(LogAnnotation.Name)

      def error: FormatterFunction =
        FormatterFunction { (builder, ctx, _) =>
          ctx
            .get(LogAnnotation.Throwable)
            .map(Cause.fail)
            .orElse(ctx.get(LogAnnotation.Cause)) match {
            case None        =>
            case Some(cause) =>
              builder.append(System.lineSeparator())
              builder.append(cause.prettyPrint)
          }
        }

      def timestamp(formatter: DateTimeFormatter): FormatterFunction =
        annotationF(LogAnnotation.Timestamp, (date: OffsetDateTime) => date.format(formatter))

      val line: FormatterFunction =
        FormatterFunction { (builder, _, line) =>
          builder.append(line)
        }
    }
  }

  final class AssembledLogFormat private (formatter: AssembledLogFormat.FormatterFunction) extends LogFormat[String] {
    private val builder = new StringBuilder()

    override def format(context: LogContext, line: String): String = {
      builder.clear()
      formatter.fun(builder, context, line)
      builder.toString()
    }
  }
}
