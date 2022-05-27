package zio.logging

import zio.logging.LogFormat.{ line, _ }
import zio.logging.internal.JsonEscape
import zio.test._
import zio.{ Cause, FiberId, LogLevel, Trace }

object JsonLogFormatSpec extends ZIOSpecDefault {
  private val nonEmptyString = Gen.stringBounded(1, 5)(Gen.alphaNumericChar)

  val spec: Spec[Environment, Any] = suite("JsonLogFormatSpec")(
    test("line") {
      val format = line
      check(nonEmptyString) { line =>
        val result = format
          .toJsonLogger(
            Trace.empty,
            FiberId.None,
            LogLevel.Info,
            () => line,
            Cause.empty,
            Map.empty,
            Nil,
            Map.empty
          )
        assertTrue(result == s"""{"text_content":"${JsonEscape(line)}"}""")
      }
    },
    test("annotation") {
      val format = annotation("test")
      check(Gen.string) { annotationValue =>
        val result = format.toJsonLogger(
          Trace.empty,
          FiberId.None,
          LogLevel.Info,
          () => "",
          Cause.empty,
          Map.empty,
          Nil,
          Map("test" -> annotationValue)
        )
        assertTrue(result == s"""{"test":"${JsonEscape(annotationValue)}"}""")
      }
    },
    test("annotation (structured)") {
      val format = annotation(LogAnnotation.UserId)
      check(Gen.string) { annotationValue =>
        val result = format.toJsonLogger(
          Trace.empty,
          FiberId.None,
          LogLevel.Info,
          () => "",
          Cause.empty,
          Map(logContext -> LogContext.empty.annotate(LogAnnotation.UserId, annotationValue)),
          Nil,
          Map.empty
        )
        assertTrue(result == s"""{"user_id":"${JsonEscape(annotationValue)}"}""")
      }
    },
    test("empty annotation") {
      val format = annotation("test")
      val result = format.toJsonLogger(
        Trace.empty,
        FiberId.None,
        LogLevel.Info,
        () => "",
        Cause.empty,
        Map.empty,
        Nil,
        Map.empty
      )
      assertTrue(result == "{}")
    },
    test("several labels") {
      val format = label("msg", line) + label("fiber", fiberId)
      check(Gen.string, Gen.int) { (line, fiberId) =>
        val result = format.toJsonLogger(
          Trace.empty,
          FiberId(fiberId, 1, Trace.empty),
          LogLevel.Info,
          () => line,
          Cause.empty,
          Map.empty,
          Nil,
          Map.empty
        )
        val msg    = JsonEscape(line)
        val fiber  = s"zio-fiber-${JsonEscape(fiberId.toString)}"
        assertTrue(result == s"""{"msg":"$msg","fiber":"$fiber"}""")
      }
    },
    test("nested labels") {
      val format = label("msg", line) + label("nested", label("fiber", fiberId) + annotation("test"))
      check(Gen.alphaNumericString, Gen.int, nonEmptyString) { (line, fiberId, annotationValue) =>
        val result = format.toJsonLogger(
          Trace.empty,
          FiberId(fiberId, 1, Trace.empty),
          LogLevel.Info,
          () => line,
          Cause.empty,
          Map.empty,
          Nil,
          Map("test" -> annotationValue)
        )
        val msg    = JsonEscape(line)
        val fiber  = s"zio-fiber-${JsonEscape(fiberId.toString)}"
        val ann    = JsonEscape(annotationValue)
        assertTrue(result == s"""{"msg":"$msg","nested":{"fiber":"$fiber","test":"$ann"}}""")
      }
    },
    test("mixed structured / unstructured ") {
      val format =
        label("msg", line) + text("hi") +
          label(
            "nested",
            label("fiber", fiberId |-| text("abc") + label("third", text("3"))) + annotation("test")
          ) + text(" there")
      check(Gen.alphaNumericString, Gen.int, nonEmptyString) { (line, fiberId, annotationValue) =>
        val result = format.toJsonLogger(
          Trace.empty,
          FiberId(fiberId, 1, Trace.empty),
          LogLevel.Info,
          () => line,
          Cause.empty,
          Map.empty,
          Nil,
          Map("test" -> annotationValue)
        )
        val msg    = JsonEscape(line)
        val fiber  = s"zio-fiber-${JsonEscape(fiberId.toString)}"
        val ann    = JsonEscape(annotationValue)
        assertTrue(
          result == s"""{"text_content":"hi there","msg":"$msg","nested":{"fiber":{"text_content":"$fiber abc","third":"3"},"test":"$ann"}}"""
        )
      }
    }
  )
}
