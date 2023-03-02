package zio.logging

import zio.test._
import zio.{ Chunk, Config, ConfigProvider }

object LogPatternSpec extends ZIOSpecDefault {

  val spec: Spec[Environment, Any] = suite("LogPattern")(
    test("parse pattern from string") {

      val pattern = LogPattern.parse("%timestamp %level xyz %message %cause %span{abc}")

      assertTrue(
        pattern == Right(
          LogPattern.Patterns(
            Chunk(
              LogPattern.Timestamp.default,
              LogPattern.Text(" "),
              LogPattern.LogLevel,
              LogPattern.Text(" xyz "),
              LogPattern.LogMessage,
              LogPattern.Text(" "),
              LogPattern.Cause,
              LogPattern.Text(" "),
              LogPattern.Span("abc")
            )
          )
        )
      )
    },
    test("parse pattern with escaped reserved chars from string") {

      val pattern =
        LogPattern.parse("%timestamp %fixed{7}{%level} %% %} xyz %message %cause %label{abcSpan}{%span{abc}}")

      assertTrue(
        pattern == Right(
          LogPattern.Patterns(
            Chunk(
              LogPattern.Timestamp.default,
              LogPattern.Text(" "),
              LogPattern.Fixed(7, LogPattern.LogLevel),
              LogPattern.Text(" "),
              LogPattern.EscapedArgPrefix,
              LogPattern.Text(" "),
              LogPattern.EscapedCloseBracket,
              LogPattern.Text(" xyz "),
              LogPattern.LogMessage,
              LogPattern.Text(" "),
              LogPattern.Cause,
              LogPattern.Text(" "),
              LogPattern.Label("abcSpan", LogPattern.Span("abc"))
            )
          )
        )
      )
    },
    test("parse pattern with highlight from string") {

      val pattern = LogPattern.parse("%timestamp %highlight{%level %{xyz%} %message %cause %span{abc}}")

      assertTrue(
        pattern == Right(
          LogPattern.Patterns(
            Chunk(
              LogPattern.Timestamp.default,
              LogPattern.Text(" "),
              LogPattern.Highlight(
                LogPattern.Patterns(
                  Chunk(
                    LogPattern.LogLevel,
                    LogPattern.Text(" "),
                    LogPattern.EscapedOpenBracket,
                    LogPattern.Text("xyz"),
                    LogPattern.EscapedCloseBracket,
                    LogPattern.Text(" "),
                    LogPattern.LogMessage,
                    LogPattern.Text(" "),
                    LogPattern.Cause,
                    LogPattern.Text(" "),
                    LogPattern.Span("abc")
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
