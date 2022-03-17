package zio.logging

import zio.logging.JsonEscape.jsonEscaped
import zio.logging.JsonLogFormat._
import zio.logging.LogFormat._
import zio.test._
import zio.{ FiberId, LogLevel, ZTraceElement }

object JsonLogFormatSpec extends DefaultRunnableSpec {
  private val nonEmptyString = Gen.stringBounded(1, 5)(Gen.unicodeChar)

  val spec: ZSpec[Environment, Failure] = suite("JsonLogFormatSpec")(
    test("nested array") {
      val format = jsonArr(text(""), line, line |-| fiberId, jsonArr(line, fiberId))
      check(nonEmptyString, Gen.int) { (line, fiberId) =>
        val result = format.toLogger(
          ZTraceElement.empty,
          FiberId(fiberId, 1),
          LogLevel.Info,
          () => line,
          Map.empty,
          Nil,
          ZTraceElement.empty,
          Map.empty
        )
        val msg    = jsonEscaped(line)
        val fiber  = s"zio-fiber-${jsonEscaped(fiberId.toString)}"
        assertTrue(result == s"""["$msg","$msg $fiber",["$msg","$fiber"]]""")
      }
    },
    test("nested object") {
      val format =
        jsonObj(
          label("msg", line),
          label("fiber", fiberId),
          label("nested", jsonObj(label("2 fibers", fiberId |-| fiberId)))
        )
      check(nonEmptyString, Gen.int) { (line, fiberId) =>
        val result = format.toLogger(
          ZTraceElement.empty,
          FiberId(fiberId, 1),
          LogLevel.Info,
          () => line,
          Map.empty,
          Nil,
          ZTraceElement.empty,
          Map.empty
        )
        val msg    = jsonEscaped(line)
        val fiber  = s"zio-fiber-${jsonEscaped(fiberId.toString)}"
        assertTrue(result == s"""{"msg":"$msg","fiber":"$fiber","nested":{"2 fibers":"$fiber $fiber"}}""")
      }
    },
    test("nested obejct array object") {
      val format =
        jsonObj(
          label("msgWithFiber", line |-| bracketed(fiberId)),
          label("arr", jsonArr(fiberId, jsonObj(label("msg", line))))
        )

      check(nonEmptyString, Gen.int) { (line, fiberId) =>
        val result = format.toLogger(
          ZTraceElement.empty,
          FiberId(fiberId, 1),
          LogLevel.Info,
          () => line,
          Map.empty,
          Nil,
          ZTraceElement.empty,
          Map.empty
        )
        val msg    = jsonEscaped(line)
        val fiber  = s"zio-fiber-${jsonEscaped(fiberId.toString)}"
        assertTrue(
          result == s"""{"msgWithFiber":"$msg [$fiber]","arr":["$fiber",{"msg":"$msg"}]}"""
        )
      }
    },
    test("mixed annotations") {
      val format = jsonObj(
        annotation("ann1", "ann2", "ann3"),
        annotation(LogAnnotation.UserId),
        annotation(LogAnnotation.TraceId),
        annotation(LogAnnotation.TraceSpans)
      )

      check(nonEmptyString, nonEmptyString, nonEmptyString, Gen.uuid) { (ann1, ann2, userId, traceId) =>
        val result = format.toLogger(
          ZTraceElement.empty,
          FiberId.None,
          LogLevel.Info,
          () => "",
          Map(
            logContext -> LogContext.empty
              .annotate(LogAnnotation.UserId, userId)
              .annotate(LogAnnotation.TraceId, traceId)
          ),
          Nil,
          ZTraceElement.empty,
          Map("ann1"   -> ann1, "ann2" -> ann2)
        )
        val a1     = jsonEscaped(ann1)
        val a2     = jsonEscaped(ann2)
        val a3     = jsonEscaped(userId)
        val a4     = jsonEscaped(traceId.toString)

        assertTrue(result == s"""{"ann1":"$a1","ann2":"$a2","user_id":"$a3","trace_id":"$a4"}""")
      }
    }
  )
}
