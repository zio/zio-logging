package zio.logging

import zio.{ FiberId, LogLevel, LogSpan, ZFiberRef, ZLogger, ZTraceElement }

import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import scala.annotation.switch

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
  ): ZLogger[Unit]

  final def +(other: LogFormat): LogFormat =
    LogFormat { (builder, trace, fiberId, level, line, fiberRefs, spans) =>
      self.unsafeFormat(builder)(trace, fiberId, level, line, fiberRefs, spans)
      other.unsafeFormat(builder)(trace, fiberId, level, line, fiberRefs, spans)
    }

  final def |-|(other: LogFormat): LogFormat =
    LogFormat { (builder, trace, fiberId, level, line, fiberRefs, spans) =>
      self.unsafeFormat(builder)(trace, fiberId, level, line, fiberRefs, spans)
      builder(" ")
      other.unsafeFormat(builder)(trace, fiberId, level, line, fiberRefs, spans)
    }

  final def color(color: LogColor): LogFormat =
    LogFormat { (builder, trace, fiberId, level, line, fiberRefs, spans) =>
      builder(color.ansi)
      self.unsafeFormat(builder)(trace, fiberId, level, line, fiberRefs, spans)
      builder(LogColor.RESET.ansi)
    }

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
    LogFormat { (builder, trace, fiberId, level, line, fiberRefs, spans) =>
      val tempBuilder = new StringBuilder
      val append      = (line: String) => {
        tempBuilder.append(line)
        ()
      }
      self.unsafeFormat(append)(trace, fiberId, level, line, fiberRefs, spans)

      val messageSize = tempBuilder.size
      if (messageSize < size) {
        builder(tempBuilder.take(size).appendAll(Array.fill(size - messageSize)(' ')).toString())
      } else {
        builder(tempBuilder.take(size).toString())
      }
    }

  final def highlight(fn: LogLevel => LogColor): LogFormat =
    LogFormat { (builder, trace, fiberId, level, line, fiberRefs, spans) =>
      builder(fn(level).ansi)
      self.unsafeFormat(builder)(trace, fiberId, level, line, fiberRefs, spans)
      builder(LogColor.RESET.ansi)
    }

  def highlight: LogFormat =
    highlight(defaultHighlighter(_))

  final def spaced(other: LogFormat): LogFormat =
    this |-| other

  final def toLogger: ZLogger[String] = (
    trace: ZTraceElement,
    fiberId: FiberId,
    logLevel: LogLevel,
    message: () => String,
    context: Map[ZFiberRef.Runtime[_], AnyRef],
    spans: List[LogSpan]
  ) => {

    val builder = new StringBuilder()
    val append  = (line: String) => {
      builder.append(line)
      ()
    }
    unsafeFormat(append)(trace, fiberId, logLevel, message, context, spans)
    builder.toString()
  }

}

object LogFormat {

  private val NL = System.lineSeparator()

  private[logging] def apply(
    format: (
      String => Unit,
      ZTraceElement,
      FiberId,
      LogLevel,
      () => String,
      Map[ZFiberRef.Runtime[_], AnyRef],
      List[LogSpan]
    ) => Any
  ): LogFormat = (builder: String => Unit) =>
    (
      trace: ZTraceElement,
      fiberId: FiberId,
      logLevel: LogLevel,
      message: () => String,
      context: Map[ZFiberRef.Runtime[_], AnyRef],
      spans: List[LogSpan]
    ) => {
      format(builder, trace, fiberId, logLevel, message, context, spans)
      ()
    }

  def annotation(name: String): LogFormat =
    LogFormat { (builder, _, _, _, _, fiberRefs, _) =>
      fiberRefs
        .get(logAnnotation)
        .foreach(value => value.asInstanceOf[Map[String, String]].get(name).foreach(builder(_)))
    }

  val annotations: LogFormat =
    LogFormat { (builder, trace, fiberId, logLevel, message, context, spans) =>
      json(
        context
          .get(logAnnotation)
          .toList
          .flatMap(value => value.asInstanceOf[Map[String, String]].view.mapValues(string).toList): _*
      ).unsafeFormat(builder)(trace, fiberId, logLevel, message, context, spans)
    }

  def bracketed(inner: LogFormat): LogFormat =
    bracketStart + inner + bracketEnd

  val bracketStart: LogFormat = string("[")

  val bracketEnd: LogFormat = string("]")

  val enclosingClass: LogFormat =
    LogFormat { (builder, trace, _, _, _, _, _) =>
      trace match {
        case ZTraceElement.SourceLocation(_, clazz, _, _) => builder(clazz)
        case _                                            => builder("not-available")
      }
    }

  val fiberNumber: LogFormat =
    LogFormat { (builder, _, fiberId, _, _, _, _) =>
      builder("zio-fiber-<")
      builder(fiberId.seqNumber.toString)
      builder(">")
    }

  val level: LogFormat =
    LogFormat { (builder, _, _, level, _, _, _) =>
      builder(level.label)
    }

  val level_value: LogFormat =
    LogFormat { (builder, _, _, level, _, _, _) =>
      builder(level.syslog.toString)
    }

  val line: LogFormat =
    LogFormat { (builder, _, _, _, line, _, _) =>
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
    LogFormat { (builder, _, _, _, _, _, _) =>
      builder(label)
    }

  val timestamp: LogFormat = timestamp(DateTimeFormatter.ISO_OFFSET_DATE_TIME)

  def timestamp(formatter: DateTimeFormatter): LogFormat =
    string {
      val now = ZonedDateTime.now()
      formatter.format(now)
    }

  def json(fields: (String, LogFormat)*): LogFormat =
    LogFormat { (builder, trace, fiberId, logLevel, message, context, spans) =>
      if (fields.isEmpty) {
        builder("{}")
      } else {
        val tempBuilder = new StringBuilder
        builder("{")
        var first       = true
        fields.foreach { case (name, format) =>
          format.unsafeFormat(tempBuilder.append(_: String))(trace, fiberId, logLevel, message, context, spans)
          val stringValue = tempBuilder.toString()
          tempBuilder.clear()
          if (!stringValue.isBlank) {
            if (first)
              first = false
            else {
              builder(",")
            }

            unsafeEncodeString(name, builder)
            builder(":")

            unsafeEncodeString(stringValue, builder)
          }
        }
        builder("}")
      }
    }

  private def unsafeEncodeString(value: String, builder: String => Unit) = {
    builder("\"")
    var i   = 0
    val len = value.length
    while (i < len) {
      (value.charAt(i): @switch) match {
        case '"'  => builder("\\\"")
        case '\\' => builder("\\\\")
        case '\b' => builder("\\b")
        case '\f' => builder("\\f")
        case '\n' => builder("\\n")
        case '\r' => builder("\\r")
        case '\t' => builder("\\t")
        case c    =>
          if (c < ' ') builder("\\u%04x".format(c.toInt))
          else builder(c.toString)
      }
      i += 1
    }
    builder("\"")
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

  val logStash: LogFormat = json(
    "@timestamp"  -> timestamp,
    "@version"    -> string("1"),
    "level"       -> level,
    "level_value" -> level_value,
    "logger_name" -> enclosingClass,
    "thread_name" -> fiberNumber,
    "message"     -> line,
    //"stack_trace" -> ???,
    "tags"        -> annotations
  )

}
