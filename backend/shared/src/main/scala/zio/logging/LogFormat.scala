package zio.logging

import zio.logging.LogFormat.defaultHighlighter
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
trait LogFormat[+Out] { self =>
  private[logging] def unsafeFormat(
    builder: Out => Unit
  ): ZLogger[Unit]

  final def +(other: LogFormat[String])(implicit ev: Out <:< String): LogFormat[String] = {
    val self = this.map(ev)
    LogFormat { (builder, trace, fiberId, level, line, fiberRefs, spans) =>
      self.unsafeFormat(builder)(trace, fiberId, level, line, fiberRefs, spans)
      other.unsafeFormat(builder)(trace, fiberId, level, line, fiberRefs, spans)
    }
  }

  final def |-|(other: LogFormat[String])(implicit ev: Out <:< String): LogFormat[String] = {
    val self = this.map(ev)
    LogFormat { (builder, trace, fiberId, level, line, fiberRefs, spans) =>
      self.unsafeFormat(builder)(trace, fiberId, level, line, fiberRefs, spans)
      builder(" ")
      other.unsafeFormat(builder)(trace, fiberId, level, line, fiberRefs, spans)
    }
  }

  final def color(color: LogColor)(implicit ev: Out <:< String): LogFormat[String] = {
    val self = this.map(ev)
    LogFormat { (builder, trace, fiberId, level, line, fiberRefs, spans) =>
      builder(color.ansi)
      self.unsafeFormat(builder)(trace, fiberId, level, line, fiberRefs, spans)
      builder(LogColor.RESET.ansi)
    }
  }

  final def concat(other: LogFormat[String])(implicit ev: Out <:< String): LogFormat[String] =
    this + other

  final def fixed(size: Int)(implicit ev: Out <:< String): LogFormat[String] = {
    val self = this.map(ev)
    LogFormat { (builder, trace, fiberId, level, line, fiberRefs, spans) =>
      val tempBuilder = new StringBuilder
      self.unsafeFormat(tempBuilder.append(_: String))(trace, fiberId, level, line, fiberRefs, spans)

      val messageSize = tempBuilder.size
      if (messageSize < size) {
        builder(tempBuilder.take(size).appendAll(Array.fill(size - messageSize)(' ')).toString())
      } else {
        builder(tempBuilder.take(size).toString())
      }
    }
  }

  final def highlight(implicit ev: Out <:< String): LogFormat[String] =
    highlight(defaultHighlighter(_))

  final def highlight(fn: LogLevel => LogColor)(implicit ev: Out <:< String): LogFormat[String] = {
    val self = this.map(ev)
    LogFormat { (builder, trace, fiberId, level, line, fiberRefs, spans) =>
      builder(fn(level).ansi)
      self.unsafeFormat(builder)(trace, fiberId, level, line, fiberRefs, spans)
      builder(LogColor.RESET.ansi)
    }
  }

  final def map[Out2](fn: Out => Out2): LogFormat[Out2] = new LogFormat[Out2] {
    override private[logging] def unsafeFormat(builder: Out2 => Unit) =
      self.unsafeFormat((out: Out) => builder(fn(out)))

    override def toLogger: ZLogger[Out2] = self.toLogger.map(fn)
  }

  final def spaced(other: LogFormat[String])(implicit ev: Out <:< String): LogFormat[String] =
    this |-| other

  def toLogger: ZLogger[Out]
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
  ): LogFormat[String] = new LogFormat[String] {
    override private[logging] def unsafeFormat(builder: String => Unit) = new ZLogger[Unit] {
      override def apply(
        trace: ZTraceElement,
        fiberId: FiberId,
        logLevel: LogLevel,
        message: () => String,
        context: Map[ZFiberRef.Runtime[_], AnyRef],
        spans: List[LogSpan]
      ): Unit = {
        format(builder, trace, fiberId, logLevel, message, context, spans)
        ()
      }
    }

    override def toLogger: ZLogger[String] = new ZLogger[String] {
      override def apply(
        trace: ZTraceElement,
        fiberId: FiberId,
        logLevel: LogLevel,
        message: () => String,
        context: Map[ZFiberRef.Runtime[_], AnyRef],
        spans: List[LogSpan]
      ): String = {
        val builder = new StringBuilder()
        //FIXME this should not be required when core will handle exception properly.
        try unsafeFormat(builder.append(_: String))(trace, fiberId, logLevel, message, context, spans)
        catch {
          case ex: Throwable => ex.printStackTrace()
        }

        builder.toString()
      }
    }
  }

  val bracketStart: LogFormat[String] = string("[")

  val bracketEnd: LogFormat[String] = string("]")

  val quote: LogFormat[String] = string("\"")

  def quoted(inner: LogFormat[String]): LogFormat[String] = quote + inner + quote

  val newLine: LogFormat[String] = string(NL)

  def bracketed(inner: LogFormat[String]): LogFormat[String] =
    bracketStart + inner + bracketEnd

  val level: LogFormat[String] =
    LogFormat { (builder, _, _, level, _, _, _) =>
      builder(level.label)
    }

  val fiberNumber: LogFormat[String] =
    LogFormat { (builder, _, fiberId, _, _, _, _) =>
      builder("#")
      builder(fiberId.seqNumber.toString)
    }

  def annotation(name: String): LogFormat[String] =
    LogFormat { (builder, _, _, _, _, fiberRefs, _) =>
      fiberRefs
        .get(logAnnotation)
        .foreach(value => value.asInstanceOf[Map[String, String]].get(name).foreach(builder(_)))
    }

  def label(label: String): LogFormat[String] =
    string(label) + string("=")

  def label(label: String, value: LogFormat[String]): LogFormat[String] =
    string(label) + string("=") + value

  def string(label: String): LogFormat[String] =
    LogFormat { (builder, _, _, _, _, _, _) =>
      builder(label)
    }

  private def defaultHighlighter(level: LogLevel) = level match {
    case LogLevel.Error   => LogColor.RED
    case LogLevel.Warning => LogColor.YELLOW
    case LogLevel.Info    => LogColor.CYAN
    case LogLevel.Debug   => LogColor.GREEN
    case _                => LogColor.WHITE
  }

  val timestamp: LogFormat[String] = timestamp(DateTimeFormatter.ISO_OFFSET_DATE_TIME)

  def timestamp(formatter: DateTimeFormatter): LogFormat[String] =
    string {
      val now = ZonedDateTime.now()
      formatter.format(now)
    }

  val line: LogFormat[String] =
    LogFormat { (builder, _, _, _, line, _, _) =>
      builder(line())
    }

  val default: LogFormat[String] =
    label("timestamp", timestamp.fixed(32)) |-|
      label("level", level) |-|
      label("thread", fiberNumber) |-|
      label("message", quoted(line))

  val colored: LogFormat[String] =
    label("timestamp", timestamp.fixed(32)).color(LogColor.BLUE) |-|
      label("level", level).highlight |-|
      label("thread", fiberNumber).color(LogColor.WHITE) |-|
      label("message", quoted(line)).highlight
}
