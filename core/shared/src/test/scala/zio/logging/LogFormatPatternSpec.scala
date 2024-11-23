package zio.logging

import zio.Chunk
import zio.logging.LogFormat.Pattern
import zio.test._

object LogFormatPatternSpec extends ZIOSpecDefault {

  val spec: Spec[Environment, Any] = suite("LogFormat.Pattern")(
    test("parse pattern from string") {

      val pattern = Pattern.parse("%timestamp %level xyz %message %cause %span{abc}")

      assertTrue(
        pattern == Right(
          Pattern.Patterns(
            Chunk(
              Pattern.Timestamp.default,
              Pattern.Text(" "),
              Pattern.LogLevel,
              Pattern.Text(" xyz "),
              Pattern.LogMessage,
              Pattern.Text(" "),
              Pattern.Cause,
              Pattern.Text(" "),
              Pattern.Span("abc")
            )
          )
        )
      )
    },
    test("parse pattern with escaped reserved chars from string") {

      val pattern =
        Pattern.parse(
          "%color{CYAN}{%timestamp} %fixed{7}{%level} %% %} xyz %message %cause %label{abcSpan}{%span{abc}}"
        )

      assertTrue(
        pattern == Right(
          Pattern.Patterns(
            Chunk(
              Pattern.Color(LogColor.CYAN, Pattern.Timestamp.default),
              Pattern.Text(" "),
              Pattern.Fixed(7, Pattern.LogLevel),
              Pattern.Text(" "),
              Pattern.EscapedArgPrefix,
              Pattern.Text(" "),
              Pattern.EscapedCloseBracket,
              Pattern.Text(" xyz "),
              Pattern.LogMessage,
              Pattern.Text(" "),
              Pattern.Cause,
              Pattern.Text(" "),
              Pattern.Label("abcSpan", Pattern.Span("abc"))
            )
          )
        )
      )
    },
    test("parse pattern with labels from string") {

      val pattern =
        Pattern.parse(
          "%label{timestamp}{%fixed{32}{%timestamp}} %label{level}{%level} %label{thread}{%fiberId} %label{message}{%message} %label{cause}{%cause}"
        )

      assertTrue(
        pattern == Right(
          Pattern.Patterns(
            Chunk(
              Pattern.Label("timestamp", Pattern.Fixed(32, Pattern.Timestamp.default)),
              Pattern.Text(" "),
              Pattern.Label("level", Pattern.LogLevel),
              Pattern.Text(" "),
              Pattern.Label("thread", Pattern.FiberId),
              Pattern.Text(" "),
              Pattern.Label("message", Pattern.LogMessage),
              Pattern.Text(" "),
              Pattern.Label("cause", Pattern.Cause)
            )
          )
        )
      )
    },
    test("parse pattern with highlight from string") {

      val pattern = Pattern.parse("%timestamp %highlight{%level %{xyz%} %message %cause %span{abc}}")

      assertTrue(
        pattern == Right(
          Pattern.Patterns(
            Chunk(
              Pattern.Timestamp.default,
              Pattern.Text(" "),
              Pattern.Highlight(
                Pattern.Patterns(
                  Chunk(
                    Pattern.LogLevel,
                    Pattern.Text(" "),
                    Pattern.EscapedOpenBracket,
                    Pattern.Text("xyz"),
                    Pattern.EscapedCloseBracket,
                    Pattern.Text(" "),
                    Pattern.LogMessage,
                    Pattern.Text(" "),
                    Pattern.Cause,
                    Pattern.Text(" "),
                    Pattern.Span("abc")
                  )
                )
              )
            )
          )
        )
      )
    }
  )
}
