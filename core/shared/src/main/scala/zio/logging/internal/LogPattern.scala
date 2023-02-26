package zio.logging.internal

import zio.logging.{ LogFilter, LogFormat, LoggerNameExtractor }
import zio.parser.{ Syntax, _ }
import zio.{ Chunk, Config }

import java.time.format.DateTimeFormatter
import scala.util.Try

sealed trait LogPattern {
  def toLogFormat: LogFormat

  def isDefinedFilter: Option[LogFilter[Any]] = None
}

object LogPattern {

  final case class Patterns(patterns: Chunk[LogPattern]) extends LogPattern {

    override def toLogFormat: LogFormat =
      patterns.map(_.toLogFormat).foldLeft(LogFormat.empty)(_ + _)

    override def isDefinedFilter: Option[LogFilter[Any]] =
      patterns.map(_.isDefinedFilter).collect { case Some(f) => f }.reduceOption(_ or _)

  }

  final case class Text(text: String) extends LogPattern {
    override def toLogFormat: LogFormat = LogFormat.text(text)
  }

  object Text {
    val empty: Text = Text("")
  }

  sealed trait Arg extends LogPattern {
    def name: String
  }

  sealed trait KeyArg[K] extends Arg {
    def key: K
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

  final case class Timestamp(key: DateTimeFormatter) extends KeyArg[DateTimeFormatter] {
    override val name = Timestamp.name

    override val toLogFormat: LogFormat = LogFormat.timestamp(key)
  }

  object Timestamp {
    val name = "timestamp"

    val default: Timestamp = Timestamp(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
  }

  final case object KeyValues extends Arg {
    override val name = "kvs"

    override val toLogFormat: LogFormat = LogFormat.allAnnotations(Set(zio.logging.loggerNameAnnotationKey))
  }

  final case class KeyValue(key: String) extends KeyArg[String] {
    override val name = KeyValue.name

    override val toLogFormat: LogFormat = LogFormat.annotation(key)
  }

  object KeyValue {
    val name: String = "kv"
  }

  final case object Spans extends Arg {
    override val name = "spans"

    override val toLogFormat: LogFormat = LogFormat.spans
  }

  final case class Span(key: String) extends KeyArg[String] {
    override val name = Span.name

    override val toLogFormat: LogFormat = LogFormat.span(key)
  }

  object Span {
    val name: String = "span"
  }

  final case object TraceLine extends Arg {
    override val name = "line"

    override val toLogFormat: LogFormat = LogFormat.traceLine
  }

  final case object Cause extends Arg {
    override val name = "cause"

    override val toLogFormat: LogFormat = LogFormat.cause

    override val isDefinedFilter: Option[LogFilter[Any]] = Some(LogFilter.causeNonEmpty)
  }

  final case class Highlight(key: LogPattern) extends KeyArg[LogPattern] {
    override val name = Highlight.name

    override val toLogFormat: LogFormat = key.toLogFormat.highlight
  }

  object Highlight {
    val name: String = "highlight"
  }

  private val argPrefix = '%'

  private def keyArgEitherSyntax[P <: KeyArg[_]](
    name: String,
    make: String => Either[String, P]
  ): Syntax[String, Char, Char, P] = {

    val begin = Syntax.string(s"${argPrefix}${name}{", ())

    val middle = Syntax
      .charNotIn('{', '}')
      .repeat
      .string
      .transformEither(
        make,
        (p: P) => Right(p.key.toString)
      )

    val end = Syntax.char('}')

    begin ~> middle <~ end
  }

  private def keyArgSyntax[P <: KeyArg[_]](name: String, make: String => P): Syntax[String, Char, Char, P] =
    keyArgEitherSyntax[P](name, s => Right(make(s)))

  private def argSyntax[P <: Arg](name: String, value: P): Syntax[String, Char, Char, P] =
    Syntax.string(s"${argPrefix}${name}", value)

  private val textSyntax =
    Syntax.charNotIn(argPrefix).repeat.string.transform(LogPattern.Text.apply, (p: LogPattern.Text) => p.text)

  private val logLevelSyntax = argSyntax(LogPattern.LogLevel.name, LogPattern.LogLevel)

  private val loggerNameSyntax = argSyntax(LogPattern.LoggerName.name, LogPattern.LoggerName)

  private val logMessageSyntax = argSyntax(LogPattern.LogMessage.name, LogPattern.LogMessage)

  private val fiberIdSyntax = argSyntax(LogPattern.FiberId.name, LogPattern.FiberId)

  private val timestampSyntax =
    keyArgEitherSyntax(
      LogPattern.Timestamp.name,
      p => Try(DateTimeFormatter.ofPattern(p)).toEither.left.map(_.getMessage).map(dtf => LogPattern.Timestamp(dtf))
    ) <> argSyntax(LogPattern.Timestamp.name, LogPattern.Timestamp.default)

  private val keyValuesSyntax = argSyntax(LogPattern.KeyValues.name, LogPattern.KeyValues)

  private val spansSyntax = argSyntax(LogPattern.Spans.name, LogPattern.Spans)

  private val causeSyntax = argSyntax(LogPattern.Cause.name, LogPattern.Cause)

  private val traceLineSyntax = argSyntax(LogPattern.TraceLine.name, LogPattern.TraceLine)

  private val keyValueSyntax = keyArgSyntax(LogPattern.KeyValue.name, LogPattern.KeyValue.apply)

  private val spanSyntax = keyArgSyntax(LogPattern.Span.name, LogPattern.Span.apply)

  private lazy val highlightSyntax = keyArgEitherSyntax(
    LogPattern.Highlight.name,
    p => syntax.parseString(p).left.map(_.toString).map(p => LogPattern.Highlight(p))
  )

  private lazy val syntax: Syntax[String, Char, Char, LogPattern] =
    (logLevelSyntax.widen[LogPattern]
      <> loggerNameSyntax.widen[LogPattern]
      <> logMessageSyntax.widen[LogPattern]
      <> fiberIdSyntax.widen[LogPattern]
      <> timestampSyntax.widen[LogPattern]
      <> keyValuesSyntax.widen[LogPattern]
      <> keyValueSyntax.widen[LogPattern]
      <> spansSyntax.widen[LogPattern]
      <> spanSyntax.widen[LogPattern]
      <> causeSyntax.widen[LogPattern]
      <> traceLineSyntax.widen[LogPattern]
      <> textSyntax.widen[LogPattern]
      <> highlightSyntax.widen[LogPattern]).repeat
      .transform[LogPattern](
        ps =>
          if (ps.size == 1) {
            ps.head
          } else LogPattern.Patterns(ps),
        _ match {
          case LogPattern.Patterns(ps) => ps
          case p: LogPattern           => Chunk(p)
        }
      )
      .widen[LogPattern]

  val config: Config[LogPattern] = Config.string.mapOrFail { value =>
    parse(value) match {
      case Right(p) => Right(p)
      case Left(l)  => Left(Config.Error.InvalidData(Chunk.empty, s"Expected a LogPattern, but found ${l}"))
    }
  }

  def parse(pattern: String): Either[Parser.ParserError[String], LogPattern] =
    syntax.parseString(pattern)

  def toLabeledLogFormat(pattern: Map[String, LogPattern]): LogFormat =
    pattern.map { case (k, p) =>
      p.isDefinedFilter match {
        case Some(f) => LogFormat.label(k, p.toLogFormat).filter(f)
        case None    => LogFormat.label(k, p.toLogFormat)
      }
    }.foldLeft(LogFormat.empty)(_ + _)
}
