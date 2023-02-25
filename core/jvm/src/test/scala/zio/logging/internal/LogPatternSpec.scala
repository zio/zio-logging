package zio.logging.internal

import zio.Chunk
import zio.test._

import java.time.format.DateTimeFormatter

object LogPatternSpec extends ZIOSpecDefault {

  val spec: Spec[Environment, Any] = suite("LogPattern")(
    test("parse") {

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
    }
  )
}
