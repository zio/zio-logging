package zio.logging

import zio.logging.JsonEscape.jsonEscaped
import zio.logging.JsonLogFormat._
import zio.logging.LogFormat._
import zio.test._
import zio.{ FiberId, LogLevel, ZTraceElement }

object JsonLogFormatSpec extends DefaultRunnableSpec {
  val spec: ZSpec[Environment, Failure] = suite("JsonLogFormatSpec")(
    test("json value") {
      val format = json(JsonValue(line))
      check(Gen.string) { line =>
        val result = format.toLogger(
          ZTraceElement.empty,
          FiberId.None,
          LogLevel.Info,
          () => line,
          Map.empty,
          Nil,
          ZTraceElement.empty,
          Map.empty
        )
        assertTrue(result == s""""${jsonEscaped(line)}"""")
      }
    },
    test("json msg") {
      val format = json(JsonObject("msg" -> JsonValue(line)))
      check(Gen.alphaNumericString) { line =>
        val result = format.toLogger(
          ZTraceElement.empty,
          FiberId.None,
          LogLevel.Info,
          () => line,
          Map.empty,
          Nil,
          ZTraceElement.empty,
          Map.empty
        )
        assertTrue(result == s"""{"msg":"${jsonEscaped(line)}"}""")
      }
    }
//    test("scoped line 2") {
//      val format = json(
//        JsonObject(
//          "msg"         -> JsonValue(line),
//          "annotations" -> JsonObject(JsonValue(annotation("ann1")), JsonValue(annotation("ann2")))
//        )
//      )
//      check(Gen.string, Gen.string, Gen.string) { (line, ann1, ann2) =>
//        val result = format.toLogger(
//          ZTraceElement.empty,
//          FiberId.None,
//          LogLevel.Info,
//          () => line,
//          Map.empty,
//          Nil,
//          ZTraceElement.empty,
//          Map("ann1" -> ann1, "ann2" -> ann2)
//        )
//        assertTrue(
//          result == s"""{"msg":"${JsonEscape(line)}",{"ann1":"${JsonEscape(ann1)}","ann2":"${JsonEscape(ann2)}"}"""
//        )
//      }
//    }
  )
}
