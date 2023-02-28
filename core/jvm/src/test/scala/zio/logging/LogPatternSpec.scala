package zio.logging

import zio.test._
import zio.{ Chunk, Config, ConfigProvider }

object LogPatternSpec extends ZIOSpecDefault {

  val spec: Spec[Environment, Any] = suite("LogPattern")(
    test("parse pattern from string") {

      val p1 = LogPattern.parse("%timestamp %level xyz %message %cause %span{abc}")

      assertTrue(
        p1 == Right(
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
    test("parse labeled pattern from config") {
      val configProvider: ConfigProvider = ConfigProvider.fromMap(
        Map(
          "pattern/timestamp" -> "%timestamp",
          "pattern/level"     -> "%level",
          "pattern/fiberId"   -> "%fiberId",
          "pattern/kvs"       -> "%kvs",
          "pattern/message"   -> "%message",
          "pattern/cause"     -> "%cause",
          "pattern/name"      -> "%name"
        ),
        "/"
      )

      val patternConfig = Config.table("pattern", LogPattern.config).withDefault(Map.empty)

      configProvider.load(patternConfig).map { labelPattern =>
        assertTrue(
          labelPattern ==
            Map(
              "timestamp" -> LogPattern.Timestamp.default,
              "level"     -> LogPattern.LogLevel,
              "fiberId"   -> LogPattern.FiberId,
              "kvs"       -> LogPattern.KeyValues,
              "message"   -> LogPattern.LogMessage,
              "cause"     -> LogPattern.Cause,
              "name"      -> LogPattern.LoggerName
            )
        )
      }
    }
  )
}
