package zio.logging.internal

import zio.{ Chunk, Config }
import zio.logging.{ LogFormat, LoggerNameExtractor }
import zio.parser.{ Syntax, _ }

object LogFormatParser {

  sealed trait Pattern {
    def toLogFormat: LogFormat
  }

  object Pattern {

    final case class Patterns(patterns: Chunk[Pattern]) extends Pattern {
      override def toLogFormat: LogFormat =
        if (patterns.isEmpty) {
          LogFormat.text("")
        } else {
          patterns.map(_.toLogFormat).reduce(_ + _)
        }
    }

    final case class Text(text: String) extends Pattern {
      override def toLogFormat: LogFormat = LogFormat.text(text)
    }

    sealed trait Arg extends Pattern {
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

    final case object Spans extends Arg {
      override val name = "%spans"

      override val toLogFormat: LogFormat = LogFormat.spans
    }

    final case object Cause extends Arg {
      override val name = "%cause"

      override val toLogFormat: LogFormat = LogFormat.cause
    }

    val config: Config[Pattern] = Config.string.mapOrFail(patternValue)
  }

  object PatternSyntax {
    private val text       = Syntax.charNotIn('%').*.string.transform(Pattern.Text.apply, (t: Pattern.Text) => t.text)
    private val logLevel   = Syntax.string(Pattern.LogLevel.name, Pattern.LogLevel)
    private val loggerName = Syntax.string(Pattern.LoggerName.name, Pattern.LoggerName)
    private val logMessage = Syntax.string(Pattern.LogMessage.name, Pattern.LogMessage)
    private val fiberId    = Syntax.string(Pattern.FiberId.name, Pattern.FiberId)
    private val timestamp  = Syntax.string(Pattern.Timestamp.name, Pattern.Timestamp)
    private val keyValues  = Syntax.string(Pattern.KeyValues.name, Pattern.KeyValues)
    private val spans      = Syntax.string(Pattern.Spans.name, Pattern.Spans)
    private val cause      = Syntax.string(Pattern.Cause.name, Pattern.Cause)

    val pattern: Syntax[String, Char, Char, Pattern] =
      (logLevel.widen[Pattern]
        <> loggerName.widen[Pattern]
        <> logMessage.widen[Pattern]
        <> fiberId.widen[Pattern]
        <> timestamp.widen[Pattern]
        <> keyValues.widen[Pattern]
        <> spans.widen[Pattern]
        <> cause.widen[Pattern]
        <> text.widen[Pattern]).repeat
        .transform(Pattern.Patterns.apply, (p: Pattern.Patterns) => p.patterns)
        .widen[Pattern]
  }

  def patternValue(value: String): Either[Config.Error.InvalidData, Pattern] =
    PatternSyntax.pattern.parseString(value) match {
      case Right(p) => Right(p)
      case Left(l)  => Left(Config.Error.InvalidData(Chunk.empty, s"Expected a Pattern, but found ${l}"))
    }
}
