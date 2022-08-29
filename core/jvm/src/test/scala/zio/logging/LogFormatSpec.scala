package zio.logging

import zio.test._
import zio.{ Cause, FiberId, FiberRefs, LogLevel, Trace }

import LogFormat.{ level, line, _ }

object LogFormatSpec extends ZIOSpecDefault {
  val spec: Spec[Environment, Any] = suite("LogFormatSpec")(
    test("line") {
      val format = line
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
      val format = level
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
      val format = levelSyslog
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
      val format = fiberId
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
    test("annotation") {
      val format = annotation("test")
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
      val format = annotation(LogAnnotation.UserId)
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
      val format = annotation("test")
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
    test("allAnnotations") {
      val format = allAnnotations
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
        assertTrue(result == s"test=${annotationValue}user_id=${annotationValue}")
      }
    },
    test("enclosing class") {
      val format = enclosingClass
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
      val format = text("a") + line + text("c")
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
      val format = line |-| text("c")
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
      val format = line.color(LogColor.RED)
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
      val format = line.fixed(10)
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
    test("cause") {
      val format = cause
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
      val format = cause

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
    }
  )
}
