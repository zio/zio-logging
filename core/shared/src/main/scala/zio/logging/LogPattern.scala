/*
 * Copyright 2019-2023 John A. De Goes and the ZIO Contributors
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

import zio.parser.{ Syntax, _ }
import zio.{ Chunk, Config }

import java.time.format.DateTimeFormatter
import scala.util.Try

/**
 * A `LogPattern` is string representation of `LogFormat`
 */
sealed trait LogPattern {
  def toLogFormat: LogFormat

  def isDefinedFilter: Option[LogFilter[Any]] = None
}

object LogPattern {

  sealed trait Arg extends LogPattern {
    def name: String
  }

  sealed trait Arg1[A1] extends Arg {
    def arg1: A1
  }

  sealed trait Arg2[A1, A2] extends Arg1[A1] {
    def arg2: A2
  }

  final case class Patterns(patterns: Chunk[LogPattern]) extends LogPattern {

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

  final case class Timestamp(arg1: DateTimeFormatter) extends Arg1[DateTimeFormatter] {
    override val name = Timestamp.name

    override val toLogFormat: LogFormat = LogFormat.timestamp(arg1)
  }

  object Timestamp {
    val name = "timestamp"

    val default: Timestamp = Timestamp(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
  }

  final case object KeyValues extends Arg {
    override val name = "kvs"

    override val toLogFormat: LogFormat = LogFormat.allAnnotations(Set(zio.logging.loggerNameAnnotationKey))
  }

  final case class KeyValue(arg1: String) extends Arg1[String] {
    override val name = KeyValue.name

    override val toLogFormat: LogFormat = LogFormat.annotation(arg1)
  }

  object KeyValue {
    val name: String = "kv"
  }

  final case object EscapedArgPrefix extends Arg {
    override val name = "%"

    override val toLogFormat: LogFormat = LogFormat.text("%")
  }

  final case object EscapedOpenBracket extends Arg {
    override val name = "{"

    override val toLogFormat: LogFormat = LogFormat.text("{")
  }

  final case object EscapedCloseBracket extends Arg {
    override val name = "}"

    override val toLogFormat: LogFormat = LogFormat.text("}")
  }

  final case object Spans extends Arg {
    override val name = "spans"

    override val toLogFormat: LogFormat = LogFormat.spans
  }

  final case class Span(arg1: String) extends Arg1[String] {
    override val name = Span.name

    override val toLogFormat: LogFormat = LogFormat.span(arg1)
  }

  object Span {
    val name: String = "span"
  }

  final case object TraceLine extends Arg {
    override val name = "line"

    override val toLogFormat: LogFormat = LogFormat.traceLine
  }

  final case class Highlight(arg1: LogPattern) extends Arg1[LogPattern] {
    override val name = Highlight.name

    override val toLogFormat: LogFormat = arg1.toLogFormat.highlight
  }

  object Highlight {
    val name: String = "highlight"
  }

  final case class Label(arg1: String, arg2: LogPattern) extends Arg2[String, LogPattern] {
    override val name = Label.name

    override val toLogFormat: LogFormat = LogFormat.label(arg1, arg2.toLogFormat)
  }

  object Label {
    val name: String = "label"
  }

  final case class Fixed(arg1: Int, arg2: LogPattern) extends Arg2[Int, LogPattern] {
    override val name = Fixed.name

    override val toLogFormat: LogFormat = arg2.toLogFormat.fixed(arg1)
  }

  object Fixed {
    val name: String = "fixed"
  }

  final case class Text(text: String) extends LogPattern {
    override def toLogFormat: LogFormat = LogFormat.text(text)
  }

  object Text {
    val empty: Text = Text("")
  }

  private val argPrefix = '%'

  private def arg1EitherSyntax[A <: Arg1[_]](
    name: String,
    make: String => Either[String, A]
  ): Syntax[String, Char, Char, A] = {

    val begin = Syntax.string(s"${argPrefix}${name}{", ())

    val middle = Syntax
      .charNotIn('{', '}')
      .repeat
      .string
      .transformEither(
        make,
        (p: A) => Right(p.arg1.toString)
      )

    val end = Syntax.char('}')

    begin ~> middle <~ end
  }

  private def arg1Syntax[A <: Arg1[_]](name: String, make: String => A): Syntax[String, Char, Char, A] =
    arg1EitherSyntax[A](name, s => Right(make(s)))

  private def arg1Syntax[A1, A <: Arg1[A1]](
    name: String,
    syntax: Syntax[String, Char, Char, A1],
    make: A1 => A
  ): Syntax[String, Char, Char, A] = {
    val begin  = Syntax.string(s"${argPrefix}${name}{", ())
    val middle = syntax.transform[A](make, _.arg1)
    val end    = Syntax.char('}')

    begin ~> middle <~ end
  }

  private def arg2Syntax[A1, A2, A <: Arg2[A1, A2]](
    name: String,
    a1Syntax: Syntax[String, Char, Char, A1],
    a2Syntax: Syntax[String, Char, Char, A2],
    make: (A1, A2) => A
  ): Syntax[String, Char, Char, A] = {
    val begin1 = Syntax.string(s"${argPrefix}${name}{", ())
    val begin2 = Syntax.char('{')
    val end    = Syntax.char('}')

    (begin1 ~> a1Syntax <~ end).zip(begin2 ~> a2Syntax <~ end).transform((v) => make(v._1, v._2), v => (v.arg1, v.arg2))
  }

  private def argSyntax[A <: Arg](name: String, value: A): Syntax[String, Char, Char, A] =
    Syntax.string(s"${argPrefix}${name}", value)

  private val intSyntax = Syntax.digit.string
    .transformEither(v => Try(v.toInt).toEither.left.map(_.getMessage), (v: Int) => Right(v.toString))

  private val stringSyntax = Syntax.charNotIn(argPrefix, '{', '}').repeat.string

  private val textSyntax = stringSyntax.transform(LogPattern.Text.apply, (p: LogPattern.Text) => p.text)

  private val logLevelSyntax = argSyntax(LogPattern.LogLevel.name, LogPattern.LogLevel)

  private val loggerNameSyntax = argSyntax(LogPattern.LoggerName.name, LogPattern.LoggerName)

  private val logMessageSyntax = argSyntax(LogPattern.LogMessage.name, LogPattern.LogMessage)

  private val fiberIdSyntax = argSyntax(LogPattern.FiberId.name, LogPattern.FiberId)

  private val timestampSyntax =
    arg1EitherSyntax(
      LogPattern.Timestamp.name,
      p => Try(DateTimeFormatter.ofPattern(p)).toEither.left.map(_.getMessage).map(dtf => LogPattern.Timestamp(dtf))
    ) <> argSyntax(LogPattern.Timestamp.name, LogPattern.Timestamp.default)

  private val keyValuesSyntax = argSyntax(LogPattern.KeyValues.name, LogPattern.KeyValues)

  private val spansSyntax = argSyntax(LogPattern.Spans.name, LogPattern.Spans)

  private val causeSyntax = argSyntax(LogPattern.Cause.name, LogPattern.Cause)

  private val traceLineSyntax = argSyntax(LogPattern.TraceLine.name, LogPattern.TraceLine)

  private val keyValueSyntax = arg1Syntax(LogPattern.KeyValue.name, LogPattern.KeyValue.apply)

  private val spanSyntax = arg1Syntax(LogPattern.Span.name, LogPattern.Span.apply)

  private val escapedArgPrefixSyntax = argSyntax(LogPattern.EscapedArgPrefix.name, LogPattern.EscapedArgPrefix)

  private val escapedEscapedOpenBracketSyntax =
    argSyntax(LogPattern.EscapedOpenBracket.name, LogPattern.EscapedOpenBracket)

  private val escapedEscapedCloseBracketSyntax =
    argSyntax(LogPattern.EscapedCloseBracket.name, LogPattern.EscapedCloseBracket)

  private lazy val highlightSyntax = arg1Syntax(LogPattern.Highlight.name, syntax, LogPattern.Highlight.apply)

  private lazy val fixedSyntax = arg2Syntax(LogPattern.Fixed.name, intSyntax, syntax, LogPattern.Fixed.apply)

  private lazy val labelSyntax = arg2Syntax(LogPattern.Label.name, stringSyntax, syntax, LogPattern.Label.apply)

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
      <> escapedArgPrefixSyntax.widen[LogPattern]
      <> escapedEscapedOpenBracketSyntax.widen[LogPattern]
      <> escapedEscapedCloseBracketSyntax.widen[LogPattern]
      <> highlightSyntax.widen[LogPattern]
      <> fixedSyntax.widen[LogPattern]
      <> labelSyntax.widen[LogPattern]
      <> textSyntax.widen[LogPattern]).repeat
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
      case Left(_)  => Left(Config.Error.InvalidData(Chunk.empty, s"Expected a LogPattern, but found ${value}"))
    }
  }

  def parse(pattern: String): Either[Parser.ParserError[String], LogPattern] =
    syntax.parseString(pattern)

}
