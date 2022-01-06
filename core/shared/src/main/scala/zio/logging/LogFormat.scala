package zio.logging

import zio.logging.LogFormat.string
import zio.{ FiberId, LogLevel, LogSpan, ZFiberRef, ZLogger, ZTraceElement }

import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/**
 * Represents DSL to build log format.
 * {{{
 * import zio.logging.LogFormat._
 * timestamp.fixed(32) |-| level |-| label("message", quoted(line))
 * }}}
 */
trait LogFormat { self =>
  private[logging] def unsafeFormat(
    builder: String => Unit
  ): ZLogger[String, Unit]

  final def +(other: LogFormat): LogFormat =
    LogFormat.string { (builder, trace, fiberId, level, line, fiberRefs, spans, location) =>
      self.unsafeFormat(builder)(trace, fiberId, level, line, fiberRefs, spans, location)
      other.unsafeFormat(builder)(trace, fiberId, level, line, fiberRefs, spans, location)
    }

  final def |-|(other: LogFormat): LogFormat =
    self + string(" ") + other

  final def color(color: LogColor): LogFormat =
    string(color.ansi) + self + string(LogColor.RESET.ansi)

  final def concat(other: LogFormat): LogFormat =
    this + other

  private def defaultHighlighter(level: LogLevel) = level match {
    case LogLevel.Error   => LogColor.RED
    case LogLevel.Warning => LogColor.YELLOW
    case LogLevel.Info    => LogColor.CYAN
    case LogLevel.Debug   => LogColor.GREEN
    case _                => LogColor.WHITE
  }

  final def fixed(size: Int): LogFormat =
    LogFormat.string { (builder, trace, fiberId, level, line, fiberRefs, spans, location) =>
      val tempBuilder = new StringBuilder
      val append      = (line: String) => {
        tempBuilder.append(line)
        ()
      }
      self.unsafeFormat(append)(trace, fiberId, level, line, fiberRefs, spans, location)

      val messageSize = tempBuilder.size
      if (messageSize < size) {
        builder(tempBuilder.take(size).appendAll(Array.fill(size - messageSize)(' ')).toString())
      } else {
        builder(tempBuilder.take(size).toString())
      }
    }

  final def highlight(fn: LogLevel => LogColor): LogFormat =
    LogFormat.string { (builder, trace, fiberId, level, line, fiberRefs, spans, location) =>
      builder(fn(level).ansi)
      self.unsafeFormat(builder)(trace, fiberId, level, line, fiberRefs, spans, location)
      builder(LogColor.RESET.ansi)
    }

  def highlight: LogFormat =
    highlight(defaultHighlighter(_))

  final def spaced(other: LogFormat): LogFormat =
    this |-| other

  final def toLogger: ZLogger[String, String] = (
    trace: ZTraceElement,
    fiberId: FiberId,
    logLevel: LogLevel,
    message: () => String,
    context: Map[ZFiberRef.Runtime[_], AnyRef],
    spans: List[LogSpan],
    location: ZTraceElement
  ) => {

    val builder = new StringBuilder()
    val append  = (line: String) => {
      builder.append(line)
      ()
    }
    unsafeFormat(append)(trace, fiberId, logLevel, message, context, spans, location)
    builder.toString()
  }

}

object LogFormat {

  private val NL = System.lineSeparator()

  private[logging] def string(
    format: (
      String => Unit,
      ZTraceElement,
      FiberId,
      LogLevel,
      () => String,
      Map[ZFiberRef.Runtime[_], AnyRef],
      List[LogSpan],
      ZTraceElement
    ) => Any
  ): LogFormat = (builder: String => Unit) =>
    (
      trace: ZTraceElement,
      fiberId: FiberId,
      logLevel: LogLevel,
      message: () => String,
      context: Map[ZFiberRef.Runtime[_], AnyRef],
      spans: List[LogSpan],
      location: ZTraceElement
    ) => {
      format(builder, trace, fiberId, logLevel, message, context, spans, location)
      ()
    }

  private[logging] def number[A: Numeric](
    format: (
      Int => Unit,
      ZTraceElement,
      FiberId,
      LogLevel,
      () => String,
      Map[ZFiberRef.Runtime[_], AnyRef],
      List[LogSpan],
      ZTraceElement
    ) => Any
  ): LogFormat = (builder: String => Unit) =>
    (
      trace: ZTraceElement,
      fiberId: FiberId,
      logLevel: LogLevel,
      message: () => String,
      context: Map[ZFiberRef.Runtime[_], AnyRef],
      spans: List[LogSpan],
      location: ZTraceElement
    ) => {
      format(builder.compose(_.toString), trace, fiberId, logLevel, message, context, spans, location)
      ()
    }

  def annotation(name: String): LogFormat =
    LogFormat.string { (builder, _, _, _, _, fiberRefs, _, _) =>
      fiberRefs
        .get(logAnnotation)
        .foreach(value => value.asInstanceOf[Map[String, String]].get(name).foreach(builder(_)))
    }

  def bracketed(inner: LogFormat): LogFormat =
    bracketStart + inner + bracketEnd

  val bracketStart: LogFormat = string("[")

  val bracketEnd: LogFormat = string("]")

  val enclosingClass: LogFormat =
    LogFormat.string { (builder, trace, _, _, _, _, _, _) =>
      trace match {
        case ZTraceElement(_, file, _) => builder(file)
        case _                         => builder("not-available")
      }
    }

  val fiberNumber: LogFormat =
    LogFormat.string { (builder, _, fiberId, _, _, _, _, _) =>
      builder("zio-fiber-<")
      builder(fiberId.ids.mkString(","))
      builder(">")
    }

  val level: LogFormat =
    LogFormat.string { (builder, _, _, level, _, _, _, _) =>
      builder(level.label)
    }

  val level_value: LogFormat =
    LogFormat.number[Int] { (builder, _, _, level, _, _, _, _) =>
      builder(level.syslog)
    }

  val line: LogFormat =
    LogFormat.string { (builder, _, _, _, line, _, _, _) =>
      builder(line())
    }

  def label(label: String): LogFormat =
    string(label) + string("=")

  def label(label: String, value: LogFormat): LogFormat =
    string(label) + string("=") + value

  val newLine: LogFormat = string(NL)

  val quote: LogFormat = string("\"")

  def quoted(inner: LogFormat): LogFormat = quote + inner + quote

  def string(label: String): LogFormat =
    LogFormat.string { (builder, _, _, _, _, _, _, _) =>
      builder(label)
    }

  val timestamp: LogFormat = timestamp(DateTimeFormatter.ISO_OFFSET_DATE_TIME)

  def timestamp(formatter: DateTimeFormatter): LogFormat =
    string {
      val now = ZonedDateTime.now()
      formatter.format(now)
    }

  val default: LogFormat =
    label("timestamp", timestamp.fixed(32)) |-|
      label("level", level) |-|
      label("thread", fiberNumber) |-|
      label("message", quoted(line))

  val colored: LogFormat =
    label("timestamp", timestamp.fixed(32)).color(LogColor.BLUE) |-|
      label("level", level).highlight |-|
      label("thread", fiberNumber).color(LogColor.WHITE) |-|
      label("message", quoted(line)).highlight

}
