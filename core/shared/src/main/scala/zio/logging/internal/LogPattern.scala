package zio.logging.internal

import zio.logging.{ LogFormat, LoggerNameExtractor }
import zio.parser.{ Syntax, _ }
import zio.{ Chunk, Config }

sealed trait LogPattern {
  def toLogFormat: LogFormat
}

object LogPattern {

  final case class Patterns(patterns: Chunk[LogPattern]) extends LogPattern {
    override def toLogFormat: LogFormat =
      if (patterns.isEmpty) {
        LogFormat.text("")
      } else {
        patterns.map(_.toLogFormat).reduce(_ + _)
      }
  }

  final case class Text(text: String) extends LogPattern {
    override def toLogFormat: LogFormat = LogFormat.text(text)
  }

  sealed trait Arg extends LogPattern {
    def name: String
  }

  final case object LogLevel extends Arg {
    override val name = "%level"

    override val toLogFormat: LogFormat = LogFormat.level
  }

  final case object LoggerName extends Arg {
    override val name = "%name"

    override val toLogFormat: LogFormat = LoggerNameExtractor.loggerNameAnnotationOrTrace.toLogFormat()
  }

  final case object LogMessage extends Arg {
    override val name = "%message"

    override val toLogFormat: LogFormat = LogFormat.line
  }

  final case object FiberId extends Arg {
    override val name = "%fiberId"

    override val toLogFormat: LogFormat = LogFormat.fiberId
  }

  final case object Timestamp extends Arg {
    override val name = "%timestamp"

    override val toLogFormat: LogFormat = LogFormat.timestamp
  }

  final case object KeyValues extends Arg {
    override val name = "%kvs"

    override val toLogFormat: LogFormat = LogFormat.allAnnotations(Set(zio.logging.loggerNameAnnotationKey))
  }

  final case class KeyValue(key: String) extends Arg {
    override val name = KeyValue.name

    override val toLogFormat: LogFormat = LogFormat.annotation(key)
  }

  object KeyValue {
    val name: String = "%kv"
  }

  final case object Spans extends Arg {
    override val name = "%spans"

    override val toLogFormat: LogFormat = LogFormat.spans
  }

  final case class Span(key: String) extends Arg {
    override val name = Span.name

    override val toLogFormat: LogFormat = LogFormat.span(key)
  }

  object Span {
    val name: String = "%span"
  }

  final case object TraceLine extends Arg {
    override val name = "%line"

    override val toLogFormat: LogFormat = LogFormat.traceLine
  }

  final case object Cause extends Arg {
    override val name = "%cause"

    override val toLogFormat: LogFormat = LogFormat.cause
  }

  private val textSyntax       =
    Syntax.charNotIn('%').repeat.string.transform(LogPattern.Text.apply, (p: LogPattern.Text) => p.text)
  private val logLevelSyntax   = Syntax.string(LogPattern.LogLevel.name, LogPattern.LogLevel)
  private val loggerNameSyntax = Syntax.string(LogPattern.LoggerName.name, LogPattern.LoggerName)
  private val logMessageSyntax = Syntax.string(LogPattern.LogMessage.name, LogPattern.LogMessage)
  private val fiberIdSyntax    = Syntax.string(LogPattern.FiberId.name, LogPattern.FiberId)
  private val timestampSyntax  = Syntax.string(LogPattern.Timestamp.name, LogPattern.Timestamp)
  private val keyValuesSyntax  = Syntax.string(LogPattern.KeyValues.name, LogPattern.KeyValues)
  private val spansSyntax      = Syntax.string(LogPattern.Spans.name, LogPattern.Spans)
  private val causeSyntax      = Syntax.string(LogPattern.Cause.name, LogPattern.Cause)
  private val traceLineSyntax  = Syntax.string(LogPattern.TraceLine.name, LogPattern.TraceLine)
  private val keyValueSyntax   =
    Syntax.string(s"${LogPattern.KeyValue.name}{", ()) ~> Syntax.alphaNumeric.repeat.string.transform(
      LogPattern.KeyValue.apply,
      (p: LogPattern.KeyValue) => p.key
    ) <~ Syntax.char('}')
  private val spanSyntax       =
    Syntax.string(s"${LogPattern.Span.name}{", ()) ~> Syntax.alphaNumeric.repeat.string.transform(
      LogPattern.Span.apply,
      (p: LogPattern.Span) => p.key
    ) <~ Syntax.char('}')

  val syntax: Syntax[String, Char, Char, LogPattern] =
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
      <> textSyntax.widen[LogPattern]).repeat
      .transform(LogPattern.Patterns.apply, (p: LogPattern.Patterns) => p.patterns)
      .widen[LogPattern]

  val config: Config[LogPattern] = Config.string.mapOrFail { value =>
    syntax.parseString(value) match {
      case Right(p) => Right(p)
      case Left(l)  => Left(Config.Error.InvalidData(Chunk.empty, s"Expected a LogPattern, but found ${l}"))
    }
  }
}
