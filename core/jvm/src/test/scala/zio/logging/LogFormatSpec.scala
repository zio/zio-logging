package zio.logging

import zio.test.{ Assertion, _ }
import zio.{ Cause, FiberId, FiberRefs, LogLevel, LogSpan, Trace }

import java.util.UUID

object LogFormatSpec extends ZIOSpecDefault {
  val spec: Spec[Environment, Any] = suite("LogFormatSpec")(
    test("line") {
      val format = LogFormat.line
      check(Gen.string) { line =>
        val result = format
          .toLogger(
            Trace.empty,
            FiberId.None,
            LogLevel.Info,
            () => line,
            Cause.empty,
            FiberRefs.empty,
            Nil,
            Map.empty
          )
        assertTrue(result == line)
      }
    },
    test("level") {
      val format = LogFormat.level
      check(Gen.elements(LogLevel.Info, LogLevel.Warning, LogLevel.Error, LogLevel.Debug)) { level =>
        val result =
          format.toLogger(
            Trace.empty,
            FiberId.None,
            level,
            () => "",
            Cause.empty,
            FiberRefs.empty,
            Nil,
            Map.empty
          )
        assertTrue(result == level.label)
      }
    },
    test("levelSyslog") {
      val format = LogFormat.levelSyslog
      check(Gen.elements(LogLevel.Info, LogLevel.Warning, LogLevel.Error, LogLevel.Debug)) { level =>
        val result =
          format.toLogger(
            Trace.empty,
            FiberId.None,
            level,
            () => "",
            Cause.empty,
            FiberRefs.empty,
            Nil,
            Map.empty
          )
        assertTrue(result == level.syslog.toString)
      }
    },
    test("fiberId") {
      val format = LogFormat.fiberId
      check(Gen.int, Gen.int) { (seq, time) =>
        val result = format.toLogger(
          Trace.empty,
          FiberId(seq, time, Trace.empty),
          LogLevel.Info,
          () => "",
          Cause.empty,
          FiberRefs.empty,
          Nil,
          Map.empty
        )
        assertTrue(result == s"zio-fiber-$seq")
      }
    },
    test("loggerName") {
      val format = LogFormat.loggerName(LoggerNameExtractor.annotation("name"))
      check(Gen.string) { annotationValue =>
        val result = format.toLogger(
          Trace.empty,
          FiberId.None,
          LogLevel.Info,
          () => "",
          Cause.empty,
          FiberRefs.empty,
          Nil,
          Map("name" -> annotationValue)
        )
        assertTrue(result == s"$annotationValue")
      }
    },
    test("annotation") {
      val format = LogFormat.annotation("test")
      check(Gen.string) { annotationValue =>
        val result = format.toLogger(
          Trace.empty,
          FiberId.None,
          LogLevel.Info,
          () => "",
          Cause.empty,
          FiberRefs.empty,
          Nil,
          Map("test" -> annotationValue)
        )
        assertTrue(result == s"test=$annotationValue")
      }
    },
    test("annotation (structured)") {
      val format = LogFormat.annotation(LogAnnotation.UserId)
      check(Gen.string) { annotationValue =>
        val result = format.toLogger(
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
        assertTrue(result == s"user_id=$annotationValue")
      }
    },
    test("any annotation") {
      val format = LogFormat.anyAnnotation("test")
      check(Gen.string) { annotationValue =>
        val result = format.toLogger(
          Trace.empty,
          FiberId.None,
          LogLevel.Info,
          () => "",
          Cause.empty,
          FiberRefs.empty,
          Nil,
          Map("test" -> annotationValue)
        )
        assertTrue(result == s"test=$annotationValue")
      }
    },
    test("any annotation (structured)") {
      val format = LogFormat.anyAnnotation(LogAnnotation.UserId.name)
      check(Gen.string) { annotationValue =>
        val result = format.toLogger(
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
        assertTrue(result == s"user_id=$annotationValue")
      }
    },
    test("empty annotation") {
      val format = LogFormat.annotation("test")
      val result = format.toLogger(
        Trace.empty,
        FiberId.None,
        LogLevel.Info,
        () => "",
        Cause.empty,
        FiberRefs.empty,
        Nil,
        Map.empty
      )
      assertTrue(result == "")
    },
    test("logAnnotations") {
      val format = LogFormat.logAnnotations
      check(Gen.string, Gen.uuid) { (userId, traceId) =>
        val result = format.toLogger(
          Trace.empty,
          FiberId.None,
          LogLevel.Info,
          () => "",
          Cause.empty,
          FiberRefs.empty.updatedAs(FiberId.Runtime(0, 0, Trace.empty))(
            logContext,
            LogContext.empty
              .annotate(LogAnnotation.UserId, userId)
              .annotate(LogAnnotation.TraceId, traceId)
          ),
          Nil,
          Map.empty
        )
        assertTrue(result == s"user_id=${userId} trace_id=${traceId}")
      }
    },
    test("allAnnotations") {
      val format = LogFormat.allAnnotations
      check(Gen.string) { annotationValue =>
        val result = format.toLogger(
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
        assertTrue(result == s"test=${annotationValue} user_id=${annotationValue}")
      }
    },
    test("allAnnotations with exclusion") {
      val format = LogFormat.allAnnotations(excludeKeys = Set("test2", LogAnnotation.TraceId.name))
      check(Gen.string) { annotationValue =>
        val result = format.toLogger(
          Trace.empty,
          FiberId.None,
          LogLevel.Info,
          () => "",
          Cause.empty,
          FiberRefs.empty.updatedAs(FiberId.Runtime(0, 0, Trace.empty))(
            logContext,
            LogContext.empty
              .annotate(LogAnnotation.UserId, annotationValue)
              .annotate(LogAnnotation.TraceId, UUID.randomUUID())
          ),
          Nil,
          Map("test" -> annotationValue, "test2" -> annotationValue)
        )
        assertTrue(result == s"test=${annotationValue} user_id=${annotationValue}")
      }
    },
    test("allAnnotations with one value") {
      val format = LogFormat.allAnnotations(excludeKeys = Set("test2", LogAnnotation.TraceId.name))
      check(Gen.string) { annotationValue =>
        val result = format.toLogger(
          Trace.empty,
          FiberId.None,
          LogLevel.Info,
          () => "",
          Cause.empty,
          FiberRefs.empty.updatedAs(FiberId.Runtime(0, 0, Trace.empty))(
            logContext,
            LogContext.empty.annotate(LogAnnotation.TraceId, UUID.randomUUID())
          ),
          Nil,
          Map("test" -> annotationValue, "test2" -> annotationValue)
        )
        assertTrue(result == s"test=${annotationValue}")
      }
    },
    test("span") {
      val format = LogFormat.span("span1")
      check(Gen.alphaNumericString) { span =>
        val result = format.toLogger(
          Trace.empty,
          FiberId.None,
          LogLevel.Info,
          () => "",
          Cause.empty,
          FiberRefs.empty,
          List(LogSpan("span1", 0L), LogSpan(span, 1L)),
          Map.empty
        )
        assert(result)(Assertion.matchesRegex("span1=([0-9]+)ms"))
      }
    },
    test("spans") {
      val format = LogFormat.spans
      check(Gen.alphaNumericString) { span =>
        val result = format.toLogger(
          Trace.empty,
          FiberId.None,
          LogLevel.Info,
          () => "",
          Cause.empty,
          FiberRefs.empty,
          List(LogSpan("span1", 0L), LogSpan(span, 1L)),
          Map.empty
        )
        assert(result)(Assertion.matchesRegex(s"span1=([0-9]+)ms ${span}=([0-9]+)ms"))
      }
    },
    test("enclosing class") {
      val format = LogFormat.enclosingClass
      val result = format
        .toLogger(
          implicitly[Trace],
          FiberId.None,
          LogLevel.Info,
          () => "",
          Cause.empty,
          FiberRefs.empty,
          Nil,
          Map.empty
        )
      assertTrue(result == "")
    } @@ TestAspect.ignore,
    test("string concat") {
      val format = LogFormat.text("a") + LogFormat.line + LogFormat.text("c")
      check(Gen.string) { line =>
        val result = format
          .toLogger(
            Trace.empty,
            FiberId.None,
            LogLevel.Info,
            () => line,
            Cause.empty,
            FiberRefs.empty,
            Nil,
            Map.empty
          )
        assertTrue(result == "a" + line + "c")
      }
    },
    test("spaced") {
      val format = LogFormat.line |-| LogFormat.text("c")
      check(Gen.string) { line =>
        val result = format
          .toLogger(
            Trace.empty,
            FiberId.None,
            LogLevel.Info,
            () => line,
            Cause.empty,
            FiberRefs.empty,
            Nil,
            Map.empty
          )
        assertTrue(result == line + " c")
      }
    },
    test("colored") {
      val format = LogFormat.line.color(LogColor.RED)
      check(Gen.string) { line =>
        val result = format
          .toLogger(
            Trace.empty,
            FiberId.None,
            LogLevel.Info,
            () => line,
            Cause.empty,
            FiberRefs.empty,
            Nil,
            Map.empty
          )
        assertTrue(result == LogColor.RED.ansi + line + LogColor.RESET.ansi)
      }
    },
    test("fixed") {
      val format = LogFormat.line.fixed(10)
      check(Gen.string) { line =>
        val result = format.toLogger(
          Trace.empty,
          FiberId.None,
          LogLevel.Info,
          () => line,
          Cause.empty,
          FiberRefs.empty,
          Nil,
          Map.empty
        )
        assertTrue(result.size == 10)
      }
    },
    test("traceLine") {
      val format = LogFormat.traceLine
      check(Gen.int) { tLine =>
        val result = format.toLogger(
          Trace("location", "file", tLine),
          FiberId.None,
          LogLevel.Info,
          () => "line",
          Cause.empty,
          FiberRefs.empty,
          Nil,
          Map.empty
        )
        assertTrue(result == tLine.toString)
      }
    },
    test("cause") {
      val format = LogFormat.cause
      check(Gen.string) { msg =>
        val failure = Cause.fail(new Exception(msg))
        val result  = format.toLogger(
          Trace.empty,
          FiberId.None,
          LogLevel.Info,
          () => "",
          failure,
          FiberRefs.empty,
          Nil,
          Map.empty
        )
        assertTrue(result == failure.prettyPrint)
      }
    },
    test("cause empty") {
      val format = LogFormat.cause

      val failure = Cause.empty
      val result  = format.toLogger(
        Trace.empty,
        FiberId.None,
        LogLevel.Info,
        () => "",
        failure,
        FiberRefs.empty,
        Nil,
        Map.empty
      )
      assertTrue(result == failure.prettyPrint)
    },
    test("not empty cause with label if not empty") {
      val format = (LogFormat.label("cause", LogFormat.cause)).filter(LogFilter.causeNonEmpty)
      check(Gen.string) { msg =>
        val failure = Cause.fail(new Exception(msg))
        val result  = format.toLogger(
          Trace.empty,
          FiberId.None,
          LogLevel.Info,
          () => "",
          failure,
          FiberRefs.empty,
          Nil,
          Map.empty
        )
        assertTrue(result == s"cause=${failure.prettyPrint}")
      }
    },
    test("empty cause with label if not empty") {
      val format = LogFormat.label("cause", LogFormat.cause).filter(LogFilter.causeNonEmpty)

      val failure = Cause.empty
      val result  = format.toLogger(
        Trace.empty,
        FiberId.None,
        LogLevel.Info,
        () => "",
        failure,
        FiberRefs.empty,
        Nil,
        Map.empty
      )
      assertTrue(result == "")
    },
    test("default without timestamp") {
      val format = LogFormat.label("level", LogFormat.level) |-|
        LogFormat.label("thread", LogFormat.fiberId) |-|
        LogFormat.label("message", LogFormat.quoted(LogFormat.line)) +
        (LogFormat.space + LogFormat.label("cause", LogFormat.cause)).filter(LogFilter.causeNonEmpty)

      check(Gen.int, Gen.string, Gen.string, Gen.boolean) { (fiberId, message, cause, hasCause) =>
        val failure                    = if (hasCause) Cause.fail(new Exception(cause)) else Cause.empty
        val result                     = format.toLogger(
          Trace.empty,
          FiberId(fiberId, 1, Trace.empty),
          LogLevel.Info,
          () => message,
          failure,
          FiberRefs.empty,
          Nil,
          Map.empty
        )
        val expectedResultWithoutCause =
          s"""level=${LogLevel.Info.label} thread=zio-fiber-$fiberId message="${message}""""
        val expectedResult             =
          if (hasCause) s"$expectedResultWithoutCause cause=${failure.prettyPrint}" else expectedResultWithoutCause
        assertTrue(result == expectedResult)
      }
    },
    test("line with filter") {
      val filter: LogFilter[String] = LogFilter[String, String](
        LogGroup[String, String]((_, _, _, line, _, _, _, _) => line()),
        _.startsWith("EXCLUDE#")
      )

      val format = LogFormat.line.filter(filter)
      check(Gen.alphaNumericString, Gen.boolean) { (msg, hasExclude) =>
        val message  = if (hasExclude) "EXCLUDE#" + msg else msg
        val expected = if (hasExclude) message else ""
        val result   = format.toLogger(
          Trace.empty,
          FiberId.None,
          LogLevel.Info,
          () => message,
          Cause.empty,
          FiberRefs.empty,
          Nil,
          Map.empty
        )
        assertTrue(result == expected)
      }
    }
  )
}
