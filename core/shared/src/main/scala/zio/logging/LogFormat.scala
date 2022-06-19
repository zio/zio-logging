/*
 * Copyright 2019-2022 John A. De Goes and the ZIO Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package zio.logging

import zio.logging.internal._
import zio.{ Cause, FiberId, FiberRef, LogLevel, LogSpan, Trace, ZLogger }

import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/**
 * A [[LogFormat]] represents a DSL to describe the format of text log messages.
 *
 * {{{
 * import zio.logging.LogFormat._
 * timestamp.fixed(32) |-| level |-| label("message", quoted(line))
 * }}}
 */
trait LogFormat { self =>
  import zio.logging.LogFormat.text

  /**
   * A low-level interface which allows efficiently building a message with a
   * mutable builder.
   */
  private[logging] def unsafeFormat(
    builder: LogAppender
  ): ZLogger[String, Unit]

  /**
   * Returns a new log format which concats both formats together without any
   * separator between them.
   */
  final def +(other: LogFormat): LogFormat =
    LogFormat.make { (builder, trace, fiberId, level, line, fiberRefs, spans, location, annotations) =>
      self.unsafeFormat(builder)(trace, fiberId, level, line, fiberRefs, spans, location, annotations)
      other.unsafeFormat(builder)(trace, fiberId, level, line, fiberRefs, spans, location, annotations)
    }

  /**
   * Returns a new log format which concats both formats together with a space
   * character between them.
   */
  final def |-|(other: LogFormat): LogFormat =
    self + text(" ") + other

  /**
   * Returns a new log format that produces the same output as this one, but
   * with the specified color applied.
   */
  final def color(color: LogColor): LogFormat =
    text(color.ansi) + self + text(LogColor.RESET.ansi)

  /**
   * The alphanumeric version of the `+` operator.
   */
  final def concat(other: LogFormat): LogFormat =
    this + other

  /**
   * Returns a new log format that produces the same as this one, but with a
   * space-padded, fixed-width output. Be careful using this operator, as it
   * destroys all structure, resulting in purely textual log output.
   */
  final def fixed(size: Int): LogFormat =
    LogFormat.make { (builder, trace, fiberId, level, line, fiberRefs, cause, spans, annotations) =>
      val tempBuilder = new StringBuilder
      val append      = LogAppender.unstructured { (line: String) =>
        tempBuilder.append(line)
        ()
      }
      self.unsafeFormat(append)(trace, fiberId, level, line, fiberRefs, cause, spans, annotations)

      val messageSize = tempBuilder.size
      if (messageSize < size) {
        builder.appendText(tempBuilder.take(size).appendAll(Array.fill(size - messageSize)(' ')).toString())
      } else {
        builder.appendText(tempBuilder.take(size).toString())
      }
    }

  /**
   * Returns a new log format that produces the same as this one, except that
   * log levels are colored according to the specified mapping.
   */
  final def highlight(fn: LogLevel => LogColor): LogFormat =
    LogFormat.make { (builder, trace, fiberId, level, line, fiberRefs, cause, spans, annotations) =>
      builder.appendText(fn(level).ansi)
      try self.unsafeFormat(builder)(trace, fiberId, level, line, fiberRefs, cause, spans, annotations)
      finally builder.appendText(LogColor.RESET.ansi)
    }

  /**
   * Returns a new log format that produces the same as this one, except that
   * the log output is highlighted.
   */
  final def highlight: LogFormat =
    highlight(defaultHighlighter(_))

  /**
   * The alphanumeric version of the `|-|` operator.
   */
  final def spaced(other: LogFormat): LogFormat =
    this |-| other

  /**
   * Converts this log format into a json logger, which accepts text input, and
   * produces json output.
   */
  final def toJsonLogger: ZLogger[String, String] = (
    trace: Trace,
    fiberId: FiberId,
    logLevel: LogLevel,
    message: () => String,
    cause: Cause[Any],
    context: Map[FiberRef[_], Any],
    spans: List[LogSpan],
    annotations: Map[String, String]
  ) => {
    val logEntryFormat =
      LogFormat.make { (builder, trace, fiberId, level, line, fiberRefs, cause, spans, annotations) =>
        builder.openLogEntry()
        try self.unsafeFormat(builder)(trace, fiberId, level, line, fiberRefs, cause, spans, annotations)
        finally builder.closeLogEntry()
      }

    val builder = new StringBuilder()
    logEntryFormat.unsafeFormat(LogAppender.json(builder.append(_)))(
      trace,
      fiberId,
      logLevel,
      message,
      cause,
      context,
      spans,
      annotations
    )
    builder.toString()
  }

  /**
   * Converts this log format into a text logger, which accepts text input, and
   * produces text output.
   */
  final def toLogger: ZLogger[String, String] = (
    trace: Trace,
    fiberId: FiberId,
    logLevel: LogLevel,
    message: () => String,
    cause: Cause[Any],
    context: Map[FiberRef[_], Any],
    spans: List[LogSpan],
    annotations: Map[String, String]
  ) => {

    val builder = new StringBuilder()
    unsafeFormat(LogAppender.unstructured(builder.append(_)))(
      trace,
      fiberId,
      logLevel,
      message,
      cause,
      context,
      spans,
      annotations
    )
    builder.toString()
  }

  private def defaultHighlighter(level: LogLevel) = level match {
    case LogLevel.Error   => LogColor.RED
    case LogLevel.Warning => LogColor.YELLOW
    case LogLevel.Info    => LogColor.CYAN
    case LogLevel.Debug   => LogColor.GREEN
    case _                => LogColor.WHITE
  }
}

object LogFormat {

  private val NL = System.lineSeparator()

  def make(
    format: (
      LogAppender,
      Trace,
      FiberId,
      LogLevel,
      () => String,
      Cause[Any],
      Map[FiberRef[_], Any],
      List[LogSpan],
      Map[String, String]
    ) => Any
  ): LogFormat = (builder: LogAppender) =>
    (
      trace: Trace,
      fiberId: FiberId,
      logLevel: LogLevel,
      message: () => String,
      cause: Cause[Any],
      context: Map[FiberRef[_], Any],
      spans: List[LogSpan],
      annotations: Map[String, String]
    ) => {
      format(builder, trace, fiberId, logLevel, message, cause, context, spans, annotations)
      ()
    }

  def annotation(name: String): LogFormat =
    LogFormat.make { (builder, _, _, _, _, _, _, _, annotations) =>
      annotations.get(name).foreach { value =>
        builder.appendKeyValue(name, value)
      }
    }

  def annotation[A](ann: LogAnnotation[A]): LogFormat =
    LogFormat.make { (builder, _, _, _, _, _, fiberRefs, _, _) =>
      fiberRefs
        .get(logContext)
        .foreach { anyRef =>
          val context = anyRef.asInstanceOf[LogContext]

          context.get(ann).foreach { value =>
            builder.appendKeyValue(ann.name, ann.render(value))
          }
        }
    }

  def bracketed(inner: LogFormat): LogFormat =
    bracketStart + inner + bracketEnd

  val bracketStart: LogFormat = text("[")

  val bracketEnd: LogFormat = text("]")

  val enclosingClass: LogFormat =
    LogFormat.make { (builder, trace, _, _, _, _, _, _, _) =>
      trace match {
        case Trace(_, file, _) => builder.appendText(file)
        case _                 => builder.appendText("not-available")
      }
    }

  val fiberId: LogFormat =
    LogFormat.make { (builder, _, fiberId, _, _, _, _, _, _) =>
      builder.appendText(fiberId.threadName)
    }

  val level: LogFormat =
    LogFormat.make { (builder, _, _, level, _, _, _, _, _) =>
      builder.appendText(level.label)
    }

  val levelSyslog: LogFormat =
    LogFormat.make { (builder, _, _, level, _, _, _, _, _) =>
      builder.appendText(level.syslog.toString)
    }

  val line: LogFormat =
    LogFormat.make { (builder, _, _, _, line, _, _, _, _) =>
      builder.appendText(line())
    }

  val cause: LogFormat =
    LogFormat.make { (builder, _, _, _, _, cause, _, _, _) =>
      builder.appendCause(cause)
    }

  def label(label: => String, value: LogFormat): LogFormat =
    LogFormat.make { (builder, trace, fiberId, logLevel, message, context, spans, location, annotations) =>
      builder.openKey()
      try builder.appendText(label)
      finally builder.closeKeyOpenValue()

      try value.unsafeFormat(builder)(trace, fiberId, logLevel, message, context, spans, location, annotations)
      finally builder.closeValue()
    }

  val newLine: LogFormat = text(NL)

  val quote: LogFormat = text("\"")

  def quoted(inner: LogFormat): LogFormat = quote + inner + quote

  def text(value: => String): LogFormat =
    LogFormat.make { (builder, _, _, _, _, _, _, _, _) =>
      builder.appendText(value)
    }

  val timestamp: LogFormat = timestamp(DateTimeFormatter.ISO_OFFSET_DATE_TIME)

  def timestamp(formatter: => DateTimeFormatter): LogFormat =
    text {
      val now = ZonedDateTime.now()
      formatter.format(now)
    }

  val default: LogFormat =
    label("timestamp", timestamp.fixed(32)) |-|
      label("level", level) |-|
      label("thread", fiberId) |-|
      label("message", quoted(line))

  val colored: LogFormat =
    label("timestamp", timestamp.fixed(32)).color(LogColor.BLUE) |-|
      label("level", level).highlight |-|
      label("thread", fiberId).color(LogColor.WHITE) |-|
      label("message", quoted(line)).highlight

}
