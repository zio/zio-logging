package zio.logging

import zio.logging.LogFormat.{ line, _ }
import zio.logging.internal.JsonEscape
import zio.test._
import zio.{ Cause, FiberId, FiberRefs, LogLevel, Trace }

object JsonLogFormatSpec extends ZIOSpecDefault {
  private val nonEmptyNonJsonString =
    Gen.stringBounded(1, 5)(Gen.alphaNumericChar).filter(isNotJson)

  private def isNotJson(s: String) = !(s.startsWith("{") || s.startsWith("["))

  val spec: Spec[Environment, Any] = suite("JsonLogFormatSpec")(
    suite("standard text")(
      test("line") {
        val format = line
        check(nonEmptyNonJsonString) { line =>
          val result = format
            .toJsonLogger(
              Trace.empty,
              FiberId.None,
              LogLevel.Info,
              () => line,
              Cause.empty,
              FiberRefs.empty,
              Nil,
              Map.empty
            )
          assertTrue(result == s"""{"text_content":"${JsonEscape(line)}"}""")
        }
      },
      test("annotation") {
        val format = annotation("test")
        check(Gen.string.filter(isNotJson)) { annotationValue =>
          val result = format.toJsonLogger(
            Trace.empty,
            FiberId.None,
            LogLevel.Info,
            () => "",
            Cause.empty,
            FiberRefs.empty,
            Nil,
            Map("test" -> annotationValue)
          )
          assertTrue(result == s"""{"test":"${JsonEscape(annotationValue)}"}""")
        }
      },
      test("annotation (structured)") {
        val format = annotation(LogAnnotation.UserId)
        check(Gen.string.filter(isNotJson)) { annotationValue =>
          val result = format.toJsonLogger(
            Trace.empty,
            FiberId.None,
            LogLevel.Info,
            () => "",
            Cause.empty,
            FiberRefs.empty.updatedAs(FiberId.Runtime(0, 0, Trace.empty))(
              logContext,
              LogContext.empty.annotate(LogAnnotation.UserId, annotationValue)
            ),
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
          FiberRefs.empty,
          Nil,
          Map.empty
        )
        assertTrue(result == "{}")
      },
      test("several labels") {
        val format = label("msg", line) + label("fiber", fiberId)
        check(Gen.string.filter(isNotJson), Gen.int) { (line, fiberId) =>
          val result = format.toJsonLogger(
            Trace.empty,
            FiberId(fiberId, 1, Trace.empty),
            LogLevel.Info,
            () => line,
            Cause.empty,
            FiberRefs.empty,
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
        check(Gen.alphaNumericString, Gen.int, nonEmptyNonJsonString) { (line, fiberId, annotationValue) =>
          val result = format.toJsonLogger(
            Trace.empty,
            FiberId(fiberId, 1, Trace.empty),
            LogLevel.Info,
            () => line,
            Cause.empty,
            FiberRefs.empty,
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
        check(Gen.alphaNumericString, Gen.int, nonEmptyNonJsonString) { (line, fiberId, annotationValue) =>
          val result = format.toJsonLogger(
            Trace.empty,
            FiberId(fiberId, 1, Trace.empty),
            LogLevel.Info,
            () => line,
            Cause.empty,
            FiberRefs.empty,
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
      },
      test("numeric value") {
        val format = label("line", traceLine)
        check(Gen.int) { i =>
          val result = format.toJsonLogger(
            Trace.apply("", "", i),
            FiberId.None,
            LogLevel.Info,
            () => "",
            Cause.empty,
            FiberRefs.empty,
            Nil,
            Map.empty
          )

          assertTrue(result == s"""{"line":$i}""")
        }
      },
      test("numeric value concatenated with string") {
        val format = label("line", traceLine |-| line)
        check(Gen.alphaNumericString, Gen.int) { (line, i) =>
          val result = format.toJsonLogger(
            Trace.apply("", "", i),
            FiberId.None,
            LogLevel.Info,
            () => line,
            Cause.empty,
            FiberRefs.empty,
            Nil,
            Map.empty
          )

          val msg = JsonEscape(s"$i $line")
          assertTrue(result == s"""{"line":"$msg"}""")
        }
      },
      test("Don't show ansi escape codes from colored log format") {
        val format = line.color(LogColor.WHITE) |-| line |-| line.color(LogColor.BLUE)
        check(nonEmptyNonJsonString) { line =>
          val result = format
            .toJsonLogger(
              Trace.empty,
              FiberId.None,
              LogLevel.Info,
              () => line,
              Cause.empty,
              FiberRefs.empty,
              Nil,
              Map.empty
            )
          val msg    = JsonEscape(line)
          assertTrue(result == s"""{"text_content":"$msg $msg $msg"}""")
        }
      }
    ),
    suite("json text")(
      test("json label") {
        val format = label("json", line)
        check(
          Gen.string.flatMap(string =>
            JsonGenerator
              .ObjectGenerator()
              .generate(List("\"" + string + "\""))
          )
        ) { line =>
          val result = format.toJsonLogger(
            Trace.empty,
            FiberId.None,
            LogLevel.Info,
            () => line,
            Cause.empty,
            FiberRefs.empty,
            Nil,
            Map.empty
          )
          val msg    = line
          assertTrue(result == s"""{"json":$msg}""")
        }
      },
      test("json annotation") {
        val format = annotation("test")
        check(
          Gen
            .listOf(Gen.alphaNumericString)
            .flatMap(list =>
              JsonGenerator
                .ObjectGenerator()
                .generate(list.map("\"" + _ + "\""))
            )
        ) { annotationValue =>
          val result = format.toJsonLogger(
            Trace.empty,
            FiberId.None,
            LogLevel.Info,
            () => "",
            Cause.empty,
            FiberRefs.empty.updatedAs(FiberId.Runtime(0, 0, Trace.empty))(
              logContext,
              LogContext.empty.annotate(LogAnnotation.UserId, annotationValue)
            ),
            Nil,
            Map("test" -> annotationValue)
          )
          assertTrue(result == s"""{"test":${annotationValue}}""")
        }
      },
      test("mixed json and non-json labels") {
        val format = label("random_json", line) + label("nonJson", text("""notA"json"""")) +
          label("static_json", text("""{"value": "I am JSON"}"""))
        check(
          Gen.string.flatMap(string =>
            JsonGenerator
              .ObjectGenerator()
              .generate(List("\"" + string + "\""))
          ),
          Gen.string.filter(isNotJson)
        ) { case (line, annotationValue) =>
          val result = format.toJsonLogger(
            Trace.empty,
            FiberId.None,
            LogLevel.Info,
            () => line,
            Cause.empty,
            FiberRefs.empty,
            Nil,
            Map.empty
          )

          val msg = line
          assertTrue(
            result == s"""{"random_json":$msg,"nonJson":"notA\\"json\\"","static_json":{"value": "I am JSON"}}"""
          )
        }
      },
      test("mixed json and non-json annotations") {
        val format = annotation("json_annotation") + annotation("nonjson_annotation")
        check(
          Gen.string.flatMap(string =>
            JsonGenerator
              .ObjectGenerator()
              .generate(List("\"" + string + "\""))
          ),
          Gen.string.filter(isNotJson)
        ) { case (jsonAnnotation, nonJsonAnnotation) =>
          val result     = format.toJsonLogger(
            Trace.empty,
            FiberId.None,
            LogLevel.Info,
            () => "",
            Cause.empty,
            FiberRefs.empty,
            Nil,
            Map("json_annotation" -> jsonAnnotation, "nonjson_annotation" -> nonJsonAnnotation)
          )
          val annEscaped = JsonEscape(nonJsonAnnotation)
          assertTrue(result == s"""{"json_annotation":$jsonAnnotation,"nonjson_annotation":"$annEscaped"}""")
        }
      },
      test("labeled empty spans") {
        val format = label("spans", spans)
        val result = format.toJsonLogger(
          Trace.empty,
          FiberId.None,
          LogLevel.Info,
          () => "",
          Cause.empty,
          FiberRefs.empty,
          Nil,
          Map.empty
        )
        assertTrue(result == s"""{"spans":null}""")
      },
      test("nested labeled empty spans and annotations") {
        val format = label("data", label("line", line) + label("spans", spans) + label("annotations", allAnnotations))
        val result = format.toJsonLogger(
          Trace.empty,
          FiberId.None,
          LogLevel.Info,
          () => "line",
          Cause.empty,
          FiberRefs.empty,
          Nil,
          Map.empty
        )
        assertTrue(result == s"""{"data":{"line":"line","spans":null,"annotations":null}}""")
      }
    )
  )

}
