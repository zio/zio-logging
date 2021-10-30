package zio.logging.backend

import zio.{ FiberId, LogLevel, LogSpan, ZFiberRef, ZLogger, ZTraceElement }

import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import scala.io.AnsiColor

object LogFormat {

  private val NL = System.lineSeparator()

  def apply(format: FormatterFunction): ZLogger[String] = (
    trace: ZTraceElement,
    fiberId: FiberId,
    logLevel: LogLevel,
    message: () => String,
    context: Map[ZFiberRef.Runtime[_], AnyRef],
    spans: List[LogSpan]
  ) => {
    val builder = new StringBuilder()
    //FIXME this should not be required when core will handle exception properly.
    try format.fun(builder, trace, fiberId, logLevel, message, context, spans)
    catch {
      case ex: Throwable => ex.printStackTrace()
    }

    builder.toString()
  }

  case class FormatterFunction(
    private[logging] val fun: (
      StringBuilder,
      ZTraceElement,
      FiberId,
      LogLevel,
      () => String,
      Map[ZFiberRef.Runtime[_], AnyRef],
      List[LogSpan]
    ) => Any
  ) {
    def +(other: FormatterFunction): FormatterFunction =
      FormatterFunction { (builder, trace, fiberId, level, line, fiberRefs, spans) =>
        fun(builder, trace, fiberId, level, line, fiberRefs, spans)
        other.fun(builder, trace, fiberId, level, line, fiberRefs, spans)
      }

    def |-|(other: FormatterFunction): FormatterFunction =
      FormatterFunction { (builder, trace, fiberId, level, line, fiberRefs, spans) =>
        fun(builder, trace, fiberId, level, line, fiberRefs, spans)
        builder.append(' ')
        other.fun(builder, trace, fiberId, level, line, fiberRefs, spans)
      }

    def concat(other: FormatterFunction): FormatterFunction =
      this + other

    def spaced(other: FormatterFunction): FormatterFunction =
      this |-| other

    def fixed(size: Int): FormatterFunction =
      FormatterFunction { (builder, trace, fiberId, level, line, fiberRefs, spans) =>
        val tempBuilder = new StringBuilder
        fun(tempBuilder, trace, fiberId, level, line, fiberRefs, spans)

        val messageSize = tempBuilder.size
        if (messageSize < size) {
          builder.append(tempBuilder.take(size).appendAll(Array.fill(size - messageSize)(' ')))
        } else {
          builder.append(tempBuilder.take(size))
        }
      }

    def color(color: LogColor): FormatterFunction =
      FormatterFunction { (builder, trace, fiberId, level, line, fiberRefs, spans) =>
        builder.append(color.ansi)
        fun(builder, trace, fiberId, level, line, fiberRefs, spans)
        builder.append(LogColor.RESET.ansi)
      }

    def highlight(fn: LogLevel => LogColor = defaultHighlighter): FormatterFunction =
      FormatterFunction { (builder, trace, fiberId, level, line, fiberRefs, spans) =>
        builder.append(fn(level).ansi)
        fun(builder, trace, fiberId, level, line, fiberRefs, spans)
        builder.append(LogColor.RESET.ansi)
      }
  }

  val space: FormatterFunction = FormatterFunction { (builder, _, _, _, _, _, _) =>
    builder.append(' ')
  }

  val bracketStart: FormatterFunction = FormatterFunction { (builder, _, _, _, _, _, _) =>
    builder.append('[')
  }

  val bracketEnd: FormatterFunction = FormatterFunction { (builder, _, _, _, _, _, _) =>
    builder.append(']')
  }

  val newLine: FormatterFunction = FormatterFunction { (builder, _, _, _, _, _, _) =>
    builder.append(NL)
  }

  def bracketed(inner: FormatterFunction): FormatterFunction =
    bracketStart + inner + bracketEnd

  val level: FormatterFunction =
    FormatterFunction { (builder, _, _, level, _, _, _) =>
      builder.append(level.label)
    }

  val fiberNumber: FormatterFunction =
    FormatterFunction { (builder, _, fiberId, _, _, _, _) =>
      builder.append("#").append(fiberId.seqNumber.toString)
    }

  def label(label: String): FormatterFunction =
    FormatterFunction { (builder, _, _, _, _, _, _) =>
      builder.append(label).append("=")
    }

  def label(label: String, value: FormatterFunction): FormatterFunction =
    FormatterFunction { (builder, trace, fiberId, level, line, fiberRefs, spans) =>
      builder.append(label).append("=")
      value.fun(builder, trace, fiberId, level, line, fiberRefs, spans)
    }

  def string(label: String): FormatterFunction =
    FormatterFunction { (builder, _, _, _, _, _, _) =>
      builder.append(label)
    }

  def withColor(color: LogColor)(inner: FormatterFunction): FormatterFunction =
    FormatterFunction { (builder, trace, fiberId, level, line, fiberRefs, spans) =>
      builder.append(color.ansi)
      inner.fun(builder, trace, fiberId, level, line, fiberRefs, spans)
      builder.append(LogColor.RESET.ansi)
    }

  private def defaultHighlighter(level: LogLevel) = level match {
    case LogLevel.Error   => LogColor.RED
    case LogLevel.Warning => LogColor.YELLOW
    case LogLevel.Info    => LogColor.CYAN
    case LogLevel.Debug   => LogColor.GREEN
    case _                => LogColor.WHITE
  }

  def highlightLog(inner: FormatterFunction, fn: LogLevel => LogColor = defaultHighlighter): FormatterFunction =
    FormatterFunction { (builder, trace, fiberId, level, line, fiberRefs, spans) =>
      builder.append(fn(level).ansi)
      inner.fun(builder, trace, fiberId, level, line, fiberRefs, spans)
      builder.append(LogColor.RESET.ansi)
    }

  def timestamp(formatter: DateTimeFormatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME): FormatterFunction =
    FormatterFunction { (builder, _, _, _, _, _, _) =>
      val now = ZonedDateTime.now()
      builder.append(formatter.format(now))
    }

  val line: FormatterFunction =
    FormatterFunction { (builder, _, _, _, line, _, _) =>
      builder.append(line())
    }

  case class LogColor private (private[logging] val ansi: String) extends AnyVal
  object LogColor {
    val RED: LogColor     = LogColor(AnsiColor.RED)
    val BLUE: LogColor    = LogColor(AnsiColor.BLUE)
    val YELLOW: LogColor  = LogColor(AnsiColor.YELLOW)
    val CYAN: LogColor    = LogColor(AnsiColor.CYAN)
    val GREEN: LogColor   = LogColor(AnsiColor.GREEN)
    val MAGENTA: LogColor = LogColor(AnsiColor.MAGENTA)
    val WHITE: LogColor   = LogColor(AnsiColor.WHITE)
    val RESET: LogColor   = LogColor(AnsiColor.RESET)
  }

  val defaultFormat: ZLogger[String] = LogFormat(
    label("timestamp", timestamp())
      |-| label("level", level)
      |-| label("thread", fiberNumber)
      |-| label("message", line)
  )

  val coloredLogFormat: ZLogger[String] = LogFormat(
    label("timestamp", timestamp().fixed(32)).color(LogColor.BLUE)
      |-| label("level", level fixed 6).highlight()
      |-| label("thread", fiberNumber).color(LogColor.WHITE)
      |-| label("message", line).highlight()
  )
}
