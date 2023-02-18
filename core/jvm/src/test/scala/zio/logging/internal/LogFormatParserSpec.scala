package zio.logging.internal

import zio.Chunk
import zio.logging.internal.LogFormatParser.Pattern
import zio.test._

object LogFormatParserSpec extends ZIOSpecDefault {

  val spec: Spec[Environment, Any] = suite("LogFormatParser")(
    test("parse") {

      val p1 = LogFormatParser.PatternSyntax.pattern.parseString("%timestamp %level xyz %message %cause")

      assertTrue(
        p1 == Right(
          Pattern.Patterns(
            Chunk(
              Pattern.Timestamp,
              Pattern.Text(" "),
              Pattern.LogLevel,
              Pattern.Text(" xyz "),
              Pattern.LogMessage,
              Pattern.Text(" "),
              Pattern.Cause,
              Pattern.Text("")
            )
          )
        )
      )
    }
  )
}
