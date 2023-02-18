package zio.logging.internal

import zio.Chunk
import zio.test._

object LogPatternSpec extends ZIOSpecDefault {

  val spec: Spec[Environment, Any] = suite("LogPattern")(
    test("parse") {

      val p1 = LogPattern.syntax.parseString("%timestamp %level xyz %message %cause")

      assertTrue(
        p1 == Right(
          LogPattern.Patterns(
            Chunk(
              LogPattern.Timestamp,
              LogPattern.Text(" "),
              LogPattern.LogLevel,
              LogPattern.Text(" xyz "),
              LogPattern.LogMessage,
              LogPattern.Text(" "),
              LogPattern.Cause,
              LogPattern.Text("")
            )
          )
        )
      )
    }
  )
}
