/*
 * Copyright 2019-2024 John A. De Goes and the ZIO Contributors
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
 * A [[LogFormat]] represents a DSL to describe the format of text log messages.
 *
 * {{{
 * import zio.logging.LogFormat._
 * timestamp.fixed(32) |-| level |-| label("message", quoted(line))
 * }}}
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
   * Returns a new log format that produces the same as this one, if filter is satisfied
   */
  final def filter[M >: String](filter: LogFilter[M]): LogFormat =
    LogFormat.FilteredFormat(self, filter)

  /**
   * Returns a new log format that produces the same as this one, but with a
   * space-padded, fixed-width output. Be careful using this operator, as it
   * destroys all structure, resulting in purely textual log output.
   */
  final def fixed(size: Int): LogFormat = LogFormat.FixedFormat(self, size)

  /**
   * Returns a new log format that produces the same as this one, except that
   * log levels are colored according to the specified mapping.
   */
  final def highlight(fn: LogLevel => LogColor): LogFormat = LogFormat.HighlightFormat(self, fn)

  /**
   * Returns a new log format that produces the same as this one, except that
   * the log output is highlighted.
   */
  final def highlight: LogFormat =
    highlight(defaultHighlighter)

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

  private val defaultHighlighter: LogLevel => LogColor = {
    case LogLevel.Error   => LogColor.RED
    case LogLevel.Warning => LogColor.YELLOW
    case LogLevel.Info    => LogColor.CYAN
    case LogLevel.Debug   => LogColor.GREEN
    case _                => LogColor.WHITE
  }
}

object LogFormat {

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
   * A `Pattern` is string representation of `LogFormat`
   */
  sealed trait Pattern {

    /**
     * Converts this log pattern into a log format.
     */
    def toLogFormat: LogFormat

    def isDefinedFilter: Option[LogFilter[Any]] = None
  }

  object Pattern {

    sealed trait Arg extends Pattern {
      def name: String
    }

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

    final case object Cause extends Arg {
      override val name = "cause"

      override val toLogFormat: LogFormat = LogFormat.cause

      override val isDefinedFilter: Option[LogFilter[Any]] = Some(LogFilter.causeNonEmpty)
    }

    final case object LogLevel extends Arg {
      override val name = "level"

      override val toLogFormat: LogFormat = LogFormat.level
    }

    final case object LoggerName extends Arg {
      override val name = "name"

      override val toLogFormat: LogFormat = LoggerNameExtractor.loggerNameAnnotationOrTrace.toLogFormat()
    }

    final case object LogMessage extends Arg {
      override val name = "message"

      override val toLogFormat: LogFormat = LogFormat.line
    }

    final case object FiberId extends Arg {
      override val name = "fiberId"

      override val toLogFormat: LogFormat = LogFormat.fiberId
    }

    final case class Timestamp(formatter: DateTimeFormatter) extends Arg {

      override val name = Timestamp.name

      override val toLogFormat: LogFormat = LogFormat.timestamp(formatter)
    }

    object Timestamp {
      val name = "timestamp"

      val default: Timestamp = Timestamp(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
    }

    final case object KeyValues extends Arg {
      override val name = "kvs"

      override val toLogFormat: LogFormat = LogFormat.allAnnotations(Set(zio.logging.loggerNameAnnotationKey))
    }

    final case class KeyValue(annotationKey: String) extends Arg {

      override val name = KeyValue.name

      override val toLogFormat: LogFormat = LogFormat.anyAnnotation(annotationKey)
    }

    object KeyValue {
      val name: String = "kv"
    }

    final case object EscapedArgPrefix extends Arg {
      override val name = "%"

      override val toLogFormat: LogFormat = LogFormat.text(name)
    }

    final case object EscapedOpenBracket extends Arg {
      override val name = "{"

      override val toLogFormat: LogFormat = LogFormat.text(name)
    }

    final case object EscapedCloseBracket extends Arg {
      override val name = "}"

      override val toLogFormat: LogFormat = LogFormat.text(name)
    }

    final case object Spans extends Arg {
      override val name = "spans"

      override val toLogFormat: LogFormat = LogFormat.spans
    }

    final case class Span(spanName: String) extends Arg {

      override val name = Span.name

      override val toLogFormat: LogFormat = LogFormat.span(spanName)
    }

    object Span {
      val name: String = "span"
    }

    final case object TraceLine extends Arg {
      override val name = "line"

      override val toLogFormat: LogFormat = LogFormat.traceLine
    }

    final case class Highlight(pattern: Pattern) extends Arg {

      override val name = Highlight.name

      override val toLogFormat: LogFormat = pattern.toLogFormat.highlight
    }

    object Highlight {
      val name: String = "highlight"
    }

    final case class Label(labelName: String, pattern: Pattern) extends Arg {

      override val name = Label.name

      override val toLogFormat: LogFormat =
        pattern.isDefinedFilter match {
          case Some(f) => LogFormat.label(labelName, pattern.toLogFormat).filter(f)
          case None    => LogFormat.label(labelName, pattern.toLogFormat)
        }
    }

    object Label {
      val name: String = "label"
    }

    final case class Fixed(size: Int, pattern: Pattern) extends Arg {

      override val name = Fixed.name

      override val toLogFormat: LogFormat = pattern.toLogFormat.fixed(size)
    }

    object Fixed {
      val name: String = "fixed"
    }

    final case class Color(color: LogColor, pattern: Pattern) extends Arg {

      override val name = Color.name

      override val toLogFormat: LogFormat = pattern.toLogFormat.color(color)
    }

    object Color {
      val name: String = "color"
    }

    final case class Text(text: String) extends Pattern {
      override val toLogFormat: LogFormat = LogFormat.text(text)
    }

    object Text {
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

    def parse(pattern: String): Either[Parser.ParserError[String], Pattern] =
      syntax.parseString(pattern).left.map(_.error)

    val config: Config[Pattern] = Config.string.mapOrFail { value =>
      Pattern.parse(value) match {
        case Right(p) => Right(p)
        case Left(_)  => Left(Config.Error.InvalidData(Chunk.empty, s"Expected a Pattern, but found ${value}"))
      }
    }
  }

  private val NL = System.lineSeparator()

  val config: Config[LogFormat] = Config.string.mapOrFail { value =>
    Pattern.parse(value) match {
      case Right(p) => Right(p.toLogFormat)
      case Left(_)  => Left(Config.Error.InvalidData(Chunk.empty, s"Expected a LogFormat, but found ${value}"))
    }
  }

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

  def loggerName(loggerNameExtractor: LoggerNameExtractor, loggerNameDefault: String = "zio-logger"): LogFormat =
    LoggerNameFormat(loggerNameExtractor, loggerNameDefault)

  def annotation(name: String): LogFormat =
    AnnotationFormat(name)

  def logAnnotation[A](ann: LogAnnotation[A]): LogFormat =
    LogAnnotationFormat(ann)

  def annotation[A](ann: LogAnnotation[A]): LogFormat = logAnnotation(ann)

  def anyAnnotation(name: String): LogFormat =
    AnyAnnotationFormat(name)

  /**
   * Returns a new log format that appends all annotations to the log output.
   */
  val annotations: LogFormat = annotations(Set.empty)

  def annotations(excludeKeys: Set[String]): LogFormat =
    AnnotationsFormat(excludeKeys)

  val logAnnotations: LogFormat = logAnnotations(Set.empty)

  def logAnnotations(excludeKeys: Set[String]): LogFormat =
    LogAnnotationsFormat(excludeKeys)

  val allAnnotations: LogFormat = allAnnotations(Set.empty)

  def allAnnotations(excludeKeys: Set[String]): LogFormat =
    AllAnnotationsFormat(excludeKeys)

  def bracketed(inner: LogFormat): LogFormat =
    bracketStart + inner + bracketEnd

  val bracketStart: LogFormat = text("[")

  val bracketEnd: LogFormat = text("]")

  val empty: LogFormat = LogFormat.make { (_, _, _, _, _, _, _, _, _) =>
    ()
  }

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

  val traceLine: LogFormat = LogFormat.make { (builder, trace, _, _, _, _, _, _, _) =>
    trace match {
      case Trace(_, _, line) => builder.appendNumeric(line)
      case _                 => ()
    }
  }

  val cause: LogFormat =
    LogFormat.make { (builder, _, _, _, _, cause, _, _, _) =>
      if (!cause.isEmpty) {
        builder.appendCause(cause)
      }
    }

  def label(label: => String, value: LogFormat): LogFormat = LabelFormat(label, value)

  val newLine: LogFormat = text(NL)

  val space: LogFormat = text(" ")

  val quote: LogFormat = text("\"")

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

  def text(value: => String): LogFormat = TextFormat(value)

  val timestamp: LogFormat = timestamp(DateTimeFormatter.ISO_OFFSET_DATE_TIME)

  def timestamp(formatter: => DateTimeFormatter): LogFormat = TimestampFormat(formatter)

  val default: LogFormat =
    label("timestamp", timestamp.fixed(32)) |-|
      label("level", level) |-|
      label("thread", fiberId) |-|
      label("message", quoted(line)) +
      (space + label("cause", cause)).filter(LogFilter.causeNonEmpty)

  val colored: LogFormat =
    label("timestamp", timestamp.fixed(32)).color(LogColor.BLUE) |-|
      label("level", level).highlight |-|
      label("thread", fiberId).color(LogColor.WHITE) |-|
      label("message", quoted(line)).highlight +
      (space + label("cause", cause).highlight).filter(LogFilter.causeNonEmpty)

}
