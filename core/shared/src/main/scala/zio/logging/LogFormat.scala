/*
 * Copyright 2019-2026 John A. De Goes and the ZIO Contributors
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
import zio.parser.{ Syntax, _ }
import zio.{ Cause, Chunk, Config, FiberId, FiberRefs, LogLevel, LogSpan, Trace, ZLogger }

import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import scala.util.Try

/**
 * A [[LogFormat]] describes how one log event is turned into a line of output. Build a format from the values
 * and functions in the `LogFormat` companion object, such as `level`, `line` and `label`, join formats with `+`
 * or `|-|`, and turn the result into a logger with `toLogger` (plain text) or `toJsonLogger` (one JSON object
 * per event). The console and file logger configs take a `LogFormat` as their `format` field.
 *
 * {{{
 * import zio.logging.LogFormat._
 *
 * val format: LogFormat = timestamp.fixed(32) |-| level |-| label("message", quoted(line))
 * // ZIO.logInfo("hello") is written as:
 * // 2026-11-23T20:33:41.765655705+03 INFO message="hello"
 * }}}
 *
 * @see [[https://zio.dev/zio-logging/formatting-log-records/ Formatting Log Records]]
 * @see [[https://zio.dev/zio-logging/reference-log-format/ LogFormat Reference]]
 */
sealed trait LogFormat { self =>
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
    LogFormat.ConcatFormat(self, other)

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
   * Returns a new log format that produces the same output as this one when `filter` accepts the log event,
   * and writes nothing when it rejects it.
   */
  final def filter[M >: String](filter: LogFilter[M]): LogFormat =
    LogFormat.FilteredFormat(self, filter)

  /**
   * Returns a new log format that produces the same as this one, but with a
   * fixed-width output: shorter output is padded with spaces up to `size`
   * characters, and longer output is cut to `size` characters. Be careful
   * using this operator, as it destroys all structure, resulting in purely
   * textual log output.
   */
  final def fixed(size: Int): LogFormat = LogFormat.FixedFormat(self, size)

  /**
   * Returns a new log format that produces the same as this one, except that
   * its whole output is colored with the color that `fn` returns for the log
   * level of the event.
   */
  final def highlight(fn: LogLevel => LogColor): LogFormat = LogFormat.HighlightFormat(self, fn)

  /**
   * Returns a new log format that produces the same as this one, except that
   * its output is colored by log level: ERROR red, WARN yellow, INFO cyan,
   * DEBUG green, and every other level white.
   */
  final def highlight: LogFormat =
    highlight(LogFormat.defaultHighlighter)

  /**
   * The alphanumeric version of the `|-|` operator.
   */
  final def spaced(other: LogFormat): LogFormat =
    this |-| other

  /**
   * Converts this log format into a json logger, which accepts text input, and
   * produces json output. Each `LogFormat.label` becomes a JSON key, and text
   * written outside any label is collected under the `text_content` key, so
   * `(level |-| line).toJsonLogger` writes `{"text_content":"INFO hello"}`.
   */
  final def toJsonLogger: ZLogger[String, String] = (
    trace: Trace,
    fiberId: FiberId,
    logLevel: LogLevel,
    message: () => String,
    cause: Cause[Any],
    context: FiberRefs,
    spans: List[LogSpan],
    annotations: Map[String, String]
  ) => {
    val logEntryFormat =
      LogFormat.make { (builder, trace, fiberId, level, line, cause, context, spans, annotations) =>
        builder.openLogEntry()
        try self.unsafeFormat(builder)(trace, fiberId, level, line, cause, context, spans, annotations)
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
    context: FiberRefs,
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
}

/**
 * The building blocks of log formats: parts that write fixed text, such as `text`, `space` and `quoted`; parts
 * that read the log event, such as `level`, `line`, `cause` and `annotation`; ready-made formats (`default`,
 * `colored`); and `config`, which reads a format from its string form.
 *
 * {{{
 * import zio._
 * import zio.logging.LogFormat
 *
 * val format: LogFormat =
 *   LogFormat.bracketed(LogFormat.level) |-| LogFormat.annotation("user") |-| LogFormat.quoted(LogFormat.line)
 *
 * // ZIO.logAnnotate("user", "alice")(ZIO.logInfo("Order placed")) is written as:
 * // [INFO] user=alice "Order placed"
 * val logger: ZLogger[String, String] = format.toLogger
 * }}}
 *
 * @see [[https://zio.dev/zio-logging/reference-log-format/ LogFormat Reference]]
 */
object LogFormat {

  private val defaultHighlighter: LogLevel => LogColor = {
    case LogLevel.Error   => LogColor.RED
    case LogLevel.Warning => LogColor.YELLOW
    case LogLevel.Info    => LogColor.CYAN
    case LogLevel.Debug   => LogColor.GREEN
    case _                => LogColor.WHITE
  }

  private[logging] def makeLogger(
    fn: (
      LogAppender,
      Trace,
      FiberId,
      LogLevel,
      () => String,
      Cause[Any],
      FiberRefs,
      List[LogSpan],
      Map[String, String]
    ) => Any
  )(
    builder: LogAppender
  ): ZLogger[String, Unit] = new ZLogger[String, Unit] {

    override def apply(
      trace: Trace,
      fiberId: FiberId,
      logLevel: LogLevel,
      message: () => String,
      cause: Cause[Any],
      context: FiberRefs,
      spans: List[LogSpan],
      annotations: Map[String, String]
    ): Unit = {
      fn(builder, trace, fiberId, logLevel, message, cause, context, spans, annotations)
      ()
    }
  }

  private[logging] final case class FnFormat(
    fn: (
      LogAppender,
      Trace,
      FiberId,
      LogLevel,
      () => String,
      Cause[Any],
      FiberRefs,
      List[LogSpan],
      Map[String, String]
    ) => Any
  ) extends LogFormat {
    override private[logging] def unsafeFormat(builder: LogAppender): ZLogger[String, Unit] =
      makeLogger(fn)(builder)
  }

  private[logging] final case class ConcatFormat(first: LogFormat, second: LogFormat) extends LogFormat {
    override private[logging] def unsafeFormat(builder: LogAppender): ZLogger[String, Unit] =
      makeLogger { (builder, trace, fiberId, level, line, cause, context, spans, annotations) =>
        first.unsafeFormat(builder)(trace, fiberId, level, line, cause, context, spans, annotations)
        second.unsafeFormat(builder)(trace, fiberId, level, line, cause, context, spans, annotations)
        ()
      }(builder)
  }

  private[logging] final case class FilteredFormat(format: LogFormat, filter: LogFilter[String]) extends LogFormat {
    override private[logging] def unsafeFormat(builder: LogAppender): ZLogger[String, Unit] =
      makeLogger { (builder, trace, fiberId, level, line, cause, context, spans, annotations) =>
        if (filter(trace, fiberId, level, line, cause, context, spans, annotations)) {
          format.unsafeFormat(builder)(trace, fiberId, level, line, cause, context, spans, annotations)
        }
        ()
      }(builder)
  }

  private[logging] final case class HighlightFormat(format: LogFormat, fn: LogLevel => LogColor) extends LogFormat {
    override private[logging] def unsafeFormat(builder: LogAppender): ZLogger[String, Unit] =
      makeLogger { (builder, trace, fiberId, level, line, cause, context, spans, annotations) =>
        builder.appendText(fn(level).ansi)
        try format.unsafeFormat(builder)(trace, fiberId, level, line, cause, context, spans, annotations)
        finally builder.appendText(LogColor.RESET.ansi)
        ()
      }(builder)
  }

  private[logging] final case class TextFormat(value: String) extends LogFormat {
    override private[logging] def unsafeFormat(builder: LogAppender): ZLogger[String, Unit] =
      makeLogger { (builder, _, _, _, _, _, _, _, _) =>
        builder.appendText(value)
      }(builder)
  }

  private[logging] final case class TimestampFormat(formatter: DateTimeFormatter) extends LogFormat {
    override private[logging] def unsafeFormat(builder: LogAppender): ZLogger[String, Unit] =
      makeLogger { (builder, _, _, _, _, _, _, _, _) =>
        val now   = ZonedDateTime.now()
        val value = formatter.format(now)

        builder.appendText(value)
      }(builder)
  }

  private[logging] final case class LabelFormat(label: String, format: LogFormat) extends LogFormat {
    override private[logging] def unsafeFormat(builder: LogAppender): ZLogger[String, Unit] =
      makeLogger { (builder, trace, fiberId, level, line, cause, context, spans, annotations) =>
        builder.openKey()
        try builder.appendText(label)
        finally builder.closeKeyOpenValue()

        try format.unsafeFormat(builder)(trace, fiberId, level, line, cause, context, spans, annotations)
        finally builder.closeValue()
      }(builder)
  }

  private[logging] final case class LoggerNameFormat(
    loggerNameExtractor: LoggerNameExtractor,
    loggerNameDefault: String
  ) extends LogFormat {
    override private[logging] def unsafeFormat(builder: LogAppender): ZLogger[String, Unit] =
      makeLogger { (builder, trace, _, _, _, _, context, _, annotations) =>
        val loggerName = loggerNameExtractor(trace, context, annotations).getOrElse(loggerNameDefault)
        builder.appendText(loggerName)
      }(builder)
  }

  private[logging] final case class FixedFormat(format: LogFormat, size: Int) extends LogFormat {
    override private[logging] def unsafeFormat(builder: LogAppender): ZLogger[String, Unit] =
      makeLogger { (builder, trace, fiberId, level, line, cause, context, spans, annotations) =>
        val tempBuilder = new StringBuilder
        val append      = LogAppender.unstructured { (line: String) =>
          tempBuilder.append(line)
          ()
        }
        format.unsafeFormat(append)(trace, fiberId, level, line, cause, context, spans, annotations)

        val messageSize = tempBuilder.size
        if (messageSize < size) {
          builder.appendText(tempBuilder.take(size).appendAll(Array.fill(size - messageSize)(' ')).toString())
        } else {
          builder.appendText(tempBuilder.take(size).toString())
        }
      }(builder)
  }

  private[logging] final case class AnnotationFormat(name: String) extends LogFormat {
    override private[logging] def unsafeFormat(builder: LogAppender): ZLogger[String, Unit] =
      makeLogger { (builder, _, _, _, _, _, _, _, annotations) =>
        annotations.get(name).foreach { value =>
          builder.appendKeyValue(name, value)
        }
      }(builder)
  }

  private[logging] final case class AnnotationsFormat(excludeKeys: Set[String]) extends LogFormat {
    override private[logging] def unsafeFormat(builder: LogAppender): ZLogger[String, Unit] =
      makeLogger { (builder, _, _, _, _, _, _, _, annotations) =>
        builder.appendKeyValues(annotations.filterNot(kv => excludeKeys.contains(kv._1)))
      }(builder)
  }

  private[logging] final case class LogAnnotationFormat[A](annotation: LogAnnotation[A]) extends LogFormat {
    override private[logging] def unsafeFormat(builder: LogAppender): ZLogger[String, Unit] =
      makeLogger { (builder, _, _, _, _, _, fiberRefs, _, _) =>
        fiberRefs
          .get(logContext)
          .foreach { context =>
            context.get(annotation).foreach { value =>
              builder.appendKeyValue(annotation.name, annotation.render(value))
            }
          }
      }(builder)
  }

  private[logging] final case class LogAnnotationsFormat(excludeKeys: Set[String]) extends LogFormat {
    override private[logging] def unsafeFormat(builder: LogAppender): ZLogger[String, Unit] =
      makeLogger { (builder, _, _, _, _, _, fiberRefs, _, _) =>
        fiberRefs
          .get(logContext)
          .foreach { context =>
            builder.appendKeyValues(context.asMap.filterNot(kv => excludeKeys.contains(kv._1)))
          }
        ()
      }(builder)
  }

  private[logging] final case class AllAnnotationsFormat(excludeKeys: Set[String]) extends LogFormat {
    override private[logging] def unsafeFormat(builder: LogAppender): ZLogger[String, Unit] =
      makeLogger { (builder, _, _, _, _, _, fiberRefs, _, annotations) =>
        val keyValues = annotations.filterNot(kv => excludeKeys.contains(kv._1)).toList ++ fiberRefs
          .get(logContext)
          .map { context =>
            context.asMap.filterNot(kv => excludeKeys.contains(kv._1)).toList
          }
          .getOrElse(Nil)
        builder.appendKeyValues(keyValues)
        ()
      }(builder)
  }

  private[logging] final case class AnyAnnotationFormat(name: String) extends LogFormat {
    override private[logging] def unsafeFormat(builder: LogAppender): ZLogger[String, Unit] =
      makeLogger { (builder, _, _, _, _, _, fiberRefs, _, annotations) =>
        annotations
          .get(name)
          .orElse(
            fiberRefs
              .get(logContext)
              .flatMap(_.get(name))
          )
          .foreach { value =>
            builder.appendKeyValue(name, value)
          }
      }(builder)
  }

  private[logging] final case class SpanFormat(name: String) extends LogFormat {
    override private[logging] def unsafeFormat(builder: LogAppender): ZLogger[String, Unit] =
      makeLogger { (builder, _, _, _, _, _, _, spans, _) =>
        spans.find(_.label == name).foreach { span =>
          val duration = (java.lang.System.currentTimeMillis() - span.startTime).toString
          builder.appendKeyValue(name, s"${duration}ms")
        }
      }(builder)
  }

  /**
   * A `Pattern` is string representation of `LogFormat`, such as `%timestamp %level %message`. Parse one with
   * `Pattern.parse`, and convert it with `toLogFormat`. `LogFormat.config` reads formats in this string form.
   *
   * @see [[https://zio.dev/zio-logging/formatting-log-records/ Formatting Log Records]]
   */
  sealed trait Pattern {

    /**
     * Converts this log pattern into a log format.
     */
    def toLogFormat: LogFormat

    /**
     * Returns the filter that a `%label{...}{...}` element around this pattern uses to decide whether to write
     * anything, or `None` for no filter. `%cause` returns `LogFilter.causeNonEmpty`, a sequence of patterns
     * returns the `or` of its parts' filters, and the other elements return `None`, also when they wrap
     * `%cause`, as `%highlight{%cause}` does.
     */
    def isDefinedFilter: Option[LogFilter[Any]] = None
  }

  /**
   * The elements of the pattern syntax, and the parser that reads a pattern string.
   */
  object Pattern {

    /**
     * A pattern element that starts with `%`, such as `%level` or `%label{name}{...}`.
     */
    sealed trait Arg extends Pattern {

      /**
       * The name of this element in the pattern string, without the `%` prefix.
       */
      def name: String
    }

    /**
     * A sequence of patterns, written one after another with no separator.
     */
    final case class Patterns(patterns: Chunk[Pattern]) extends Pattern {

      override def toLogFormat: LogFormat = {
        val formats = patterns.map(_.toLogFormat)
        if (formats.isEmpty) {
          LogFormat.empty
        } else formats.reduce(_ + _)
      }

      override def isDefinedFilter: Option[LogFilter[Any]] =
        patterns.map(_.isDefinedFilter).collect { case Some(f) => f }.reduceOption(_ or _)

    }

    /** The `%cause` element: the cause of the log event, written only when it is not empty. */
    final case object Cause extends Arg {
      override val name = "cause"

      override val toLogFormat: LogFormat = LogFormat.cause

      override val isDefinedFilter: Option[LogFilter[Any]] = Some(LogFilter.causeNonEmpty)
    }

    /** The `%level` element: the log level label, such as `INFO`. */
    final case object LogLevel extends Arg {
      override val name = "level"

      override val toLogFormat: LogFormat = LogFormat.level
    }

    /**
     * The `%name` element: the logger name, from the `logger_name` annotation, or from the `Trace` of the log
     * call when the annotation is not set.
     */
    final case object LoggerName extends Arg {
      override val name = "name"

      override val toLogFormat: LogFormat = LoggerNameExtractor.loggerNameAnnotationOrTrace.toLogFormat()
    }

    /** The `%message` element: the log message. */
    final case object LogMessage extends Arg {
      override val name = "message"

      override val toLogFormat: LogFormat = LogFormat.line
    }

    /** The `%fiberId` element: the thread name of the fiber that logged the event, such as `zio-fiber-7`. */
    final case object FiberId extends Arg {
      override val name = "fiberId"

      override val toLogFormat: LogFormat = LogFormat.fiberId
    }

    /**
     * The `%timestamp{pattern}` element: the current time, formatted with a `DateTimeFormatter` built from
     * `pattern`. `%timestamp` without braces uses `DateTimeFormatter.ISO_OFFSET_DATE_TIME`.
     */
    final case class Timestamp(formatter: DateTimeFormatter) extends Arg {

      override val name = Timestamp.name

      override val toLogFormat: LogFormat = LogFormat.timestamp(formatter)
    }

    /** The name of the `%timestamp` element. */
    object Timestamp {

      /** The element name, `timestamp`. */
      val name = "timestamp"

      /** The `%timestamp` element without a pattern, which uses `DateTimeFormatter.ISO_OFFSET_DATE_TIME`. */
      val default: Timestamp = Timestamp(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
    }

    /**
     * The `%kvs` element: every string annotation of the event and every entry of the log context, except the
     * logger name annotation, as `key=value` pairs.
     */
    final case object KeyValues extends Arg {
      override val name = "kvs"

      override val toLogFormat: LogFormat = LogFormat.allAnnotations(Set(zio.logging.loggerNameAnnotationKey))
    }

    /**
     * The `%kv{key}` element: the annotation `key` as `key=value`, taken from the string annotations of the
     * event, or from the log context when the event has no such string annotation.
     */
    final case class KeyValue(annotationKey: String) extends Arg {

      override val name = KeyValue.name

      override val toLogFormat: LogFormat = LogFormat.anyAnnotation(annotationKey)
    }

    /** The name of the `%kv` element. */
    object KeyValue {

      /** The element name, `kv`. */
      val name: String = "kv"
    }

    /** The `%%` element: a literal `%` character. */
    final case object EscapedArgPrefix extends Arg {
      override val name = "%"

      override val toLogFormat: LogFormat = LogFormat.text(name)
    }

    /** The `%{` element: a literal `{` character. */
    final case object EscapedOpenBracket extends Arg {
      override val name = "{"

      override val toLogFormat: LogFormat = LogFormat.text(name)
    }

    /** The `%}` element: a literal `}` character. */
    final case object EscapedCloseBracket extends Arg {
      override val name = "}"

      override val toLogFormat: LogFormat = LogFormat.text(name)
    }

    /** The `%spans` element: every log span of the event, with its elapsed time, such as `db=12ms`. */
    final case object Spans extends Arg {
      override val name = "spans"

      override val toLogFormat: LogFormat = LogFormat.spans
    }

    /** The `%span{name}` element: the log span `name` with its elapsed time, such as `db=12ms`, if the event has it. */
    final case class Span(spanName: String) extends Arg {

      override val name = Span.name

      override val toLogFormat: LogFormat = LogFormat.span(spanName)
    }

    /** The name of the `%span` element. */
    object Span {

      /** The element name, `span`. */
      val name: String = "span"
    }

    /** The `%line` element: the source line number of the log call, from its `Trace`. */
    final case object TraceLine extends Arg {
      override val name = "line"

      override val toLogFormat: LogFormat = LogFormat.traceLine
    }

    /** The `%highlight{...}` element: the inner pattern, colored by log level as `LogFormat#highlight` does. */
    final case class Highlight(pattern: Pattern) extends Arg {

      override val name = Highlight.name

      override val toLogFormat: LogFormat = pattern.toLogFormat.highlight
    }

    /** The name of the `%highlight` element. */
    object Highlight {

      /** The element name, `highlight`. */
      val name: String = "highlight"
    }

    /**
     * The `%label{name}{...}` element: the inner pattern as the value of the key `name`, as `LogFormat.label`
     * does. When the inner pattern has an `isDefinedFilter`, such as `%cause`, the label is written only for
     * the events that the filter accepts.
     */
    final case class Label(labelName: String, pattern: Pattern) extends Arg {

      override val name = Label.name

      override val toLogFormat: LogFormat =
        pattern.isDefinedFilter match {
          case Some(f) => LogFormat.label(labelName, pattern.toLogFormat).filter(f)
          case None    => LogFormat.label(labelName, pattern.toLogFormat)
        }
    }

    /** The name of the `%label` element. */
    object Label {

      /** The element name, `label`. */
      val name: String = "label"
    }

    /** The `%fixed{size}{...}` element: the inner pattern, padded or cut to `size` characters. */
    final case class Fixed(size: Int, pattern: Pattern) extends Arg {

      override val name = Fixed.name

      override val toLogFormat: LogFormat = pattern.toLogFormat.fixed(size)
    }

    /** The name of the `%fixed` element. */
    object Fixed {

      /** The element name, `fixed`. */
      val name: String = "fixed"
    }

    /**
     * The `%color{COLOR}{...}` element: the inner pattern in the given color. `COLOR` is one of `RED`, `BLUE`,
     * `YELLOW`, `CYAN`, `GREEN`, `MAGENTA`, `WHITE` or `RESET`, in upper case: `%color{red}{...}` does not parse.
     */
    final case class Color(color: LogColor, pattern: Pattern) extends Arg {

      override val name = Color.name

      override val toLogFormat: LogFormat = pattern.toLogFormat.color(color)
    }

    /** The name of the `%color` element. */
    object Color {

      /** The element name, `color`. */
      val name: String = "color"
    }

    /** Literal text, written as it is. */
    final case class Text(text: String) extends Pattern {
      override val toLogFormat: LogFormat = LogFormat.text(text)
    }

    /** Constants for literal text patterns. */
    object Text {

      /** Text with no characters. */
      val empty: Text = Text("")
    }

    private val argPrefix = '%'

    private def arg1EitherSyntax[A <: Arg](
      name: String,
      make: String => Either[String, A],
      extract: A => Either[String, String]
    ): Syntax[String, Char, Char, A] = {

      val begin = Syntax.string(s"${argPrefix}${name}{", ())

      val middle = Syntax
        .charNotIn('{', '}')
        .repeat
        .string
        .transformEither(
          make,
          extract
        )

      val end = Syntax.char('}')

      begin ~> middle <~ end
    }

    private def arg1Syntax[A <: Arg](
      name: String,
      make: String => A,
      extract: A => String
    ): Syntax[String, Char, Char, A] =
      arg1EitherSyntax[A](name, v => Right(make(v)), v => Right(extract(v)))

    private def arg1Syntax[A <: Arg, A1](
      name: String,
      syntax: Syntax[String, Char, Char, A1],
      make: A1 => A,
      extract: A => A1
    ): Syntax[String, Char, Char, A] = {
      val begin  = Syntax.string(s"${argPrefix}${name}{", ())
      val middle = syntax.transform[A](make, extract)
      val end    = Syntax.char('}')

      begin ~> middle <~ end
    }

    private def arg2Syntax[A <: Arg, A1, A2](
      name: String,
      a1Syntax: Syntax[String, Char, Char, A1],
      a2Syntax: Syntax[String, Char, Char, A2],
      make: (A1, A2) => A,
      extract: A => (A1, A2)
    ): Syntax[String, Char, Char, A] = {
      val begin1 = Syntax.string(s"${argPrefix}${name}{", ())
      val begin2 = Syntax.char('{')
      val end    = Syntax.char('}')

      (begin1 ~> a1Syntax <~ end).zip(begin2 ~> a2Syntax <~ end).transform(v => make(v._1, v._2), extract)
    }

    private def argSyntax[A <: Arg](name: String, value: A): Syntax[String, Char, Char, A] =
      Syntax.string(s"${argPrefix}${name}", value)

    private val intSyntax = Syntax.digit.repeat.string
      .transformEither(v => Try(v.toInt).toEither.left.map(_.getMessage), (v: Int) => Right(v.toString))

    private val stringSyntax = Syntax.charNotIn(argPrefix, '{', '}').repeat.string

    private val logColorSyntax = stringSyntax
      .transformEither(
        v => LogColor.logColorMapping.get(v).toRight(s"Unknown value: $v"),
        (v: LogColor) => Right(v.getClass.getSimpleName)
      )

    private val textSyntax = stringSyntax.transform(Pattern.Text.apply, (p: Pattern.Text) => p.text)

    private val logLevelSyntax = argSyntax(Pattern.LogLevel.name, Pattern.LogLevel)

    private val loggerNameSyntax = argSyntax(Pattern.LoggerName.name, Pattern.LoggerName)

    private val logMessageSyntax = argSyntax(Pattern.LogMessage.name, Pattern.LogMessage)

    private val fiberIdSyntax = argSyntax(Pattern.FiberId.name, Pattern.FiberId)

    private val timestampSyntax =
      arg1EitherSyntax[Pattern.Timestamp](
        Pattern.Timestamp.name,
        p => Try(DateTimeFormatter.ofPattern(p)).toEither.left.map(_.getMessage).map(dtf => Pattern.Timestamp(dtf)),
        p => Right(p.formatter.toString)
      ) <> argSyntax(Pattern.Timestamp.name, Pattern.Timestamp.default)

    private val keyValuesSyntax = argSyntax(Pattern.KeyValues.name, Pattern.KeyValues)

    private val spansSyntax = argSyntax(Pattern.Spans.name, Pattern.Spans)

    private val causeSyntax = argSyntax(Pattern.Cause.name, Pattern.Cause)

    private val traceLineSyntax = argSyntax(Pattern.TraceLine.name, Pattern.TraceLine)

    private val keyValueSyntax =
      arg1Syntax[Pattern.KeyValue](Pattern.KeyValue.name, Pattern.KeyValue.apply, _.annotationKey)

    private val spanSyntax = arg1Syntax[Pattern.Span](Pattern.Span.name, Pattern.Span.apply, _.spanName)

    private val escapedArgPrefixSyntax = argSyntax(Pattern.EscapedArgPrefix.name, Pattern.EscapedArgPrefix)

    private val escapedEscapedOpenBracketSyntax =
      argSyntax(Pattern.EscapedOpenBracket.name, Pattern.EscapedOpenBracket)

    private val escapedEscapedCloseBracketSyntax =
      argSyntax(Pattern.EscapedCloseBracket.name, Pattern.EscapedCloseBracket)

    private lazy val highlightSyntax =
      arg1Syntax(
        Pattern.Highlight.name,
        syntax,
        Pattern.Highlight.apply,
        (p: Pattern.Highlight) => p.pattern
      )

    private lazy val fixedSyntax =
      arg2Syntax(
        Pattern.Fixed.name,
        intSyntax,
        syntax,
        Pattern.Fixed.apply,
        (p: Pattern.Fixed) => (p.size, p.pattern)
      )

    private lazy val labelSyntax =
      arg2Syntax(
        Pattern.Label.name,
        stringSyntax,
        syntax,
        Pattern.Label.apply,
        (p: Pattern.Label) => (p.labelName, p.pattern)
      )

    private lazy val colorSyntax =
      arg2Syntax(
        Pattern.Color.name,
        logColorSyntax,
        syntax,
        Pattern.Color.apply,
        (p: Pattern.Color) => (p.color, p.pattern)
      )

    private lazy val syntax: Syntax[String, Char, Char, Pattern] =
      (logLevelSyntax.widen[Pattern]
        <> loggerNameSyntax.widen[Pattern]
        <> logMessageSyntax.widen[Pattern]
        <> fiberIdSyntax.widen[Pattern]
        <> timestampSyntax.widen[Pattern]
        <> keyValuesSyntax.widen[Pattern]
        <> keyValueSyntax.widen[Pattern]
        <> spansSyntax.widen[Pattern]
        <> spanSyntax.widen[Pattern]
        <> causeSyntax.widen[Pattern]
        <> traceLineSyntax.widen[Pattern]
        <> escapedArgPrefixSyntax.widen[Pattern]
        <> escapedEscapedOpenBracketSyntax.widen[Pattern]
        <> escapedEscapedCloseBracketSyntax.widen[Pattern]
        <> highlightSyntax.widen[Pattern]
        <> fixedSyntax.widen[Pattern]
        <> labelSyntax.widen[Pattern]
        <> colorSyntax.widen[Pattern]
        <> textSyntax.widen[Pattern]).repeat
        .transform[Pattern](
          ps =>
            if (ps.size == 1) {
              ps.head
            } else Pattern.Patterns(ps),
          _ match {
            case Pattern.Patterns(ps) => ps
            case p: Pattern           => Chunk(p)
          }
        )
        .widen[Pattern]

    /**
     * Parses a pattern string, such as `%timestamp{HH:mm:ss} %level %message`.
     *
     * Returns `Left` when the string does not start with text or a known element, for example `%unknown` or an
     * empty string. Parsing stops at the first point where no element or text matches, such as a lone `}` or an
     * unknown element after some text, and the rest of the string is ignored: `%level}xyz` parses as `%level`,
     * and `abc %unknown` as the text `abc `.
     *
     * {{{
     * import zio.logging.LogFormat.Pattern
     *
     * val parsed = Pattern.parse("%level %message")
     * // Right(Patterns(Chunk(LogLevel,Text( ),LogMessage)))
     * val format: Option[LogFormat] = parsed.toOption.map(_.toLogFormat)
     * }}}
     */
    def parse(pattern: String): Either[Parser.ParserError[String], Pattern] =
      syntax.parseString(pattern).left.map(_.error)

    /**
     * Reads a `Pattern` from a string. A string that `Pattern.parse` rejects fails with the message
     * `Expected a Pattern, but found ` followed by the string.
     */
    val config: Config[Pattern] = Config.string.mapOrFail { value =>
      Pattern.parse(value) match {
        case Right(p) => Right(p)
        case Left(_)  => Left(Config.Error.InvalidData(Chunk.empty, s"Expected a Pattern, but found ${value}"))
      }
    }
  }

  private val NL = System.lineSeparator()

  /**
   * Reads a log format from a pattern string, such as `%label{level}{%level} %label{message}{%message}`; see
   * `LogFormat.Pattern` for the elements. A string that `Pattern.parse` rejects fails with the message
   * `Expected a LogFormat, but found ` followed by the string.
   *
   * {{{
   * import zio._
   * import zio.logging.LogFormat
   *
   * val provider = ConfigProvider.fromMap(Map("format" -> "%level [%name] %message"))
   * val format: IO[Config.Error, LogFormat] =
   *   provider.load(LogFormat.config.nested("format"))
   * }}}
   *
   * @see [[https://zio.dev/zio-logging/formatting-log-records/ Formatting Log Records]]
   */
  val config: Config[LogFormat] = Config.string.mapOrFail { value =>
    Pattern.parse(value) match {
      case Right(p) => Right(p.toLogFormat)
      case Left(_)  => Left(Config.Error.InvalidData(Chunk.empty, s"Expected a LogFormat, but found ${value}"))
    }
  }

  /**
   * Creates a log format from a function. The function receives an appender, which collects the output, and
   * the parts of the log event: trace, fiber id, level, message, cause, fiber refs, spans and string
   * annotations. Write output with the appender's `appendText`, `appendNumeric`, `appendCause` and
   * `appendKeyValue` methods; the function's result is ignored.
   *
   * {{{
   * import zio.logging.LogFormat
   *
   * val lowerCaseLevel: LogFormat =
   *   LogFormat.make { (builder, _, _, level, message, _, _, _, _) =>
   *     builder.appendText(level.label.toLowerCase + ": " + message())
   *   }
   * // ZIO.logInfo("hello") is written as: info: hello
   * }}}
   */
  def make(
    format: (
      LogAppender,
      Trace,
      FiberId,
      LogLevel,
      () => String,
      Cause[Any],
      FiberRefs,
      List[LogSpan],
      Map[String, String]
    ) => Any
  ): LogFormat = FnFormat(format)

  /**
   * Writes the logger name that `loggerNameExtractor` finds, or `loggerNameDefault` when it finds none.
   */
  def loggerName(loggerNameExtractor: LoggerNameExtractor, loggerNameDefault: String = "zio-logger"): LogFormat =
    LoggerNameFormat(loggerNameExtractor, loggerNameDefault)

  /**
   * Writes the string annotation `name` of the log event, as `name=value`, such as one set with
   * `ZIO.logAnnotate(name, value)`. Writes nothing when the event has no such annotation.
   */
  def annotation(name: String): LogFormat =
    AnnotationFormat(name)

  /**
   * Writes the value of the typed annotation `ann` from the log context, rendered with its `render` function,
   * as `name=value`. Writes nothing when the log context has no value for it.
   *
   * {{{
   * import zio.logging.{ LogAnnotation, LogFormat }
   *
   * val format: LogFormat = LogFormat.line |-| LogFormat.logAnnotation(LogAnnotation.UserId)
   *
   * // ZIO.logInfo("Signed in") @@ LogAnnotation.UserId("u-42") is written as:
   * // Signed in user_id=u-42
   * }}}
   */
  def logAnnotation[A](ann: LogAnnotation[A]): LogFormat =
    LogAnnotationFormat(ann)

  /**
   * Same as `logAnnotation`: writes the value of the typed annotation `ann` from the log context, as
   * `name=value`.
   */
  def annotation[A](ann: LogAnnotation[A]): LogFormat = logAnnotation(ann)

  /**
   * Writes the annotation `name`, as `name=value`. It looks first in the string annotations of the log event,
   * then in the log context. Writes nothing when neither has it.
   */
  def anyAnnotation(name: String): LogFormat =
    AnyAnnotationFormat(name)

  /**
   * Returns a new log format that appends all string annotations of the log event to the log output, as
   * `key=value` pairs. Typed annotations from the log context are not included; see `allAnnotations`.
   */
  val annotations: LogFormat = annotations(Set.empty)

  /**
   * Writes the string annotations of the log event, as `key=value` pairs, except those whose key is in
   * `excludeKeys`.
   */
  def annotations(excludeKeys: Set[String]): LogFormat =
    AnnotationsFormat(excludeKeys)

  /**
   * Writes every typed annotation in the log context, as `name=value` pairs.
   */
  val logAnnotations: LogFormat = logAnnotations(Set.empty)

  /**
   * Writes every typed annotation in the log context, as `name=value` pairs, except those whose name is in
   * `excludeKeys`.
   */
  def logAnnotations(excludeKeys: Set[String]): LogFormat =
    LogAnnotationsFormat(excludeKeys)

  /**
   * Writes the string annotations of the log event, then the typed annotations in the log context, as
   * `key=value` pairs. A key present in both is written twice.
   */
  val allAnnotations: LogFormat = allAnnotations(Set.empty)

  /**
   * Same as `allAnnotations`, except that keys in `excludeKeys` are skipped in both sources.
   */
  def allAnnotations(excludeKeys: Set[String]): LogFormat =
    AllAnnotationsFormat(excludeKeys)

  /**
   * Wraps the output of `inner` in square brackets, as in `[INFO]`.
   */
  def bracketed(inner: LogFormat): LogFormat =
    bracketStart + inner + bracketEnd

  /** Writes `[`. */
  val bracketStart: LogFormat = text("[")

  /** Writes `]`. */
  val bracketEnd: LogFormat = text("]")

  /** Writes nothing. */
  val empty: LogFormat = LogFormat.make { (_, _, _, _, _, _, _, _, _) =>
    ()
  }

  /**
   * Writes the source file name of the log call, such as `Main.scala`, taken from its `Trace`, or
   * `not-available` when the trace cannot be parsed.
   */
  val enclosingClass: LogFormat =
    LogFormat.make { (builder, trace, _, _, _, _, _, _, _) =>
      val parsed = Trace.parseOrNull(trace)
      if (parsed ne null) {
        builder.appendText(parsed.file)
      } else {
        builder.appendText("not-available")
      }
    }

  /** Writes the thread name of the fiber that logged the event, such as `zio-fiber-7`. */
  val fiberId: LogFormat =
    LogFormat.make { (builder, _, fiberId, _, _, _, _, _, _) =>
      builder.appendText(fiberId.threadName)
    }

  /** Writes the label of the log level, such as `INFO` or `WARN`. */
  val level: LogFormat =
    LogFormat.make { (builder, _, _, level, _, _, _, _, _) =>
      builder.appendText(level.label)
    }

  /**
   * Writes the syslog severity number of the log level: `2` for FATAL, `3` for ERROR, `4` for WARN, `6` for
   * INFO, and `7` for DEBUG and TRACE.
   */
  val levelSyslog: LogFormat =
    LogFormat.make { (builder, _, _, level, _, _, _, _, _) =>
      builder.appendText(level.syslog.toString)
    }

  /** Writes the log message. */
  val line: LogFormat =
    LogFormat.make { (builder, _, _, _, line, _, _, _, _) =>
      builder.appendText(line())
    }

  /**
   * Writes the source line number of the log call, taken from its `Trace`, or nothing when the trace cannot
   * be parsed.
   */
  val traceLine: LogFormat = LogFormat.make { (builder, trace, _, _, _, _, _, _, _) =>
    val parsed = Trace.parseOrNull(trace)
    if (parsed ne null) {
      builder.appendNumeric(parsed.line)
    }
  }

  /** Writes the cause of the log event, pretty-printed, or nothing when the cause is empty. */
  val cause: LogFormat =
    LogFormat.make { (builder, _, _, _, _, cause, _, _, _) =>
      if (!cause.isEmpty) {
        builder.appendCause(cause)
      }
    }

  /**
   * Writes the output of `value` under the key `label`: `label=value` in text output, and a `"label"` key in
   * JSON output. `label` is evaluated once, when the format is built.
   *
   * {{{
   * import zio.logging.LogFormat
   *
   * val format: LogFormat = LogFormat.label("level", LogFormat.level) |-| LogFormat.label("msg", LogFormat.line)
   * // ZIO.logInfo("hello") with format.toLogger: level=INFO msg=hello
   * // ZIO.logInfo("hello") with format.toJsonLogger: {"level":"INFO","msg":"hello"}
   * }}}
   */
  def label(label: => String, value: LogFormat): LogFormat = LabelFormat(label, value)

  /** Writes the line separator of the platform. */
  val newLine: LogFormat = text(NL)

  /** Writes one space. */
  val space: LogFormat = text(" ")

  /** Writes one double quote character. */
  val quote: LogFormat = text("\"")

  /** Wraps the output of `inner` in double quotes. */
  def quoted(inner: LogFormat): LogFormat = quote + inner + quote

  /**
   * Returns a new log format that appends the specified span to the log output.
   */
  def span(name: String): LogFormat =
    SpanFormat(name)

  /**
   * Returns a new log format that appends all spans to the log output.
   */
  val spans: LogFormat =
    LogFormat.make { (builder, _, _, _, _, _, _, spans, _) =>
      builder.appendKeyValues(spans.map { span =>
        val duration = (java.lang.System.currentTimeMillis() - span.startTime).toString
        span.label -> s"${duration}ms"
      })
    }

  /**
   * Writes `value`. `value` is evaluated once, when the format is built, not for each log event.
   */
  def text(value: => String): LogFormat = TextFormat(value)

  /**
   * Writes the current time, read when the line is written, formatted with
   * `DateTimeFormatter.ISO_OFFSET_DATE_TIME`, such as `2026-11-23T20:33:41.776529474+03:30`.
   */
  val timestamp: LogFormat = timestamp(DateTimeFormatter.ISO_OFFSET_DATE_TIME)

  /**
   * Writes the current time, read when the line is written, formatted with `formatter`. `formatter` is
   * evaluated once, when the format is built.
   */
  def timestamp(formatter: => DateTimeFormatter): LogFormat = TimestampFormat(formatter)

  /**
   * The default format of the console and file loggers: the labelled parts `timestamp` (cut or padded to 32
   * characters), `level`, `thread` (the fiber name) and `message` (quoted), separated by spaces, followed by
   * `cause` only when the cause is not empty.
   *
   * {{{
   * import zio.logging.LogFormat
   *
   * val format: LogFormat = LogFormat.default
   * // ZIO.logInfo("hello") is written as:
   * // timestamp=2026-11-23T20:33:41.118883052+03 level=INFO thread=zio-fiber-334602018 message="hello"
   * }}}
   */
  val default: LogFormat =
    label("timestamp", timestamp.fixed(32)) |-|
      label("level", level) |-|
      label("thread", fiberId) |-|
      label("message", quoted(line)) +
      (space + label("cause", cause)).filter(LogFilter.causeNonEmpty)

  /**
   * The same parts as `default`, with ANSI colors: the timestamp blue, the thread white, and the level, the
   * message and the cause colored by log level, as `LogFormat#highlight` does.
   */
  val colored: LogFormat =
    label("timestamp", timestamp.fixed(32)).color(LogColor.BLUE) |-|
      label("level", level).highlight |-|
      label("thread", fiberId).color(LogColor.WHITE) |-|
      label("message", quoted(line)).highlight +
      (space + label("cause", cause).highlight).filter(LogFilter.causeNonEmpty)

}
