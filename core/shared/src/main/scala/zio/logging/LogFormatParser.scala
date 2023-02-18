package zio.logging
import zio.Chunk
import zio.parser.{ Syntax, _ }

object LogFormatParser {

  sealed trait Pattern

  object Pattern {

    final case class Text(text: String) extends Pattern

    sealed trait Arg extends Pattern {
      def name: String
    }

    final case object LogLevel extends Arg {
      override val name = "%level"
    }

    final case object LoggerName extends Arg {
      override val name = "%name"
    }

    final case object LogMessage extends Arg {
      override val name = "%message"
    }

    final case object FiberId extends Arg {
      override val name = "%fiberId"
    }

    final case object Timestamp extends Arg {
      override val name = "%timestamp"
    }

    final case object KeyValues extends Arg {
      override val name = "%kvs"
    }

  }

  val text = Syntax.charNotIn('%').*.string.transform(Pattern.Text.apply, (t: Pattern.Text) => t.text)

  val logLevelArg   = Syntax.string(Pattern.LogLevel.name, Pattern.LogLevel)
  val loggerNameArg = Syntax.string(Pattern.LoggerName.name, Pattern.LoggerName)
  val logMessageArg = Syntax.string(Pattern.LogMessage.name, Pattern.LogMessage)
  val fiberIdArg    = Syntax.string(Pattern.FiberId.name, Pattern.FiberId)
  val timestampArg  = Syntax.string(Pattern.Timestamp.name, Pattern.Timestamp)
  val keyValuesArg  = Syntax.string(Pattern.KeyValues.name, Pattern.KeyValues)

  val pattern: Syntax[String, Char, Char, Chunk[Pattern]] = (logLevelArg.widen[Pattern]
    <> loggerNameArg.widen[Pattern]
    <> logMessageArg.widen[Pattern]
    <> fiberIdArg.widen[Pattern]
    <> timestampArg.widen[Pattern]
    <> keyValuesArg.widen[Pattern] <> text.widen[Pattern]).repeat

}
