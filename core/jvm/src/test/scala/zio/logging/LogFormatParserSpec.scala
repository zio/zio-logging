package zio.logging

import zio.Chunk
import zio.test._
import LogFormatParser.Pattern
object LogFormatParserSpec extends ZIOSpecDefault {

  val spec: Spec[Environment, Any] = suite("LogFormatParser")(
    test("parse") {

      val p1 = LogFormatParser.pattern.parseString("%timestamp %level xyx %message")

      assertTrue(
        p1 == Right(Chunk[Pattern](Pattern.Timestamp, Pattern.Text(" "), Pattern.LogLevel, Pattern.Text(" xyz "), Pattern.LogMessage, Pattern.Text("")))
      )
    }
  )
}
