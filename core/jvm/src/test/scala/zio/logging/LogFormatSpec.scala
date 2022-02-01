package zio.logging

import zio.test._
import zio.{ FiberId, LogLevel, ZTraceElement }

import LogFormat.{ level, line, _ }

object LogFormatSpec extends DefaultRunnableSpec {
  val spec: ZSpec[Environment, Failure] = suite("LogFormatSpec")(
    test("line") {
      val format = line
      check(Gen.string) { line =>
        val result = format
          .toLogger(ZTraceElement.empty, FiberId.None, LogLevel.Info, () => line, Map.empty, Nil, ZTraceElement.empty, Map.empty)
        assertTrue(result == line)
      }
    },
    test("level") {
      val format = level
      check(Gen.elements(LogLevel.Info, LogLevel.Warning, LogLevel.Error, LogLevel.Debug)) { level =>
        val result =
          format.toLogger(ZTraceElement.empty, FiberId.None, level, () => "", Map.empty, Nil, ZTraceElement.empty, Map.empty)
        assertTrue(result == level.label)
      }
    },
    test("levelSyslog") {
      val format = levelSyslog
      check(Gen.elements(LogLevel.Info, LogLevel.Warning, LogLevel.Error, LogLevel.Debug)) { level =>
        val result =
          format.toLogger(ZTraceElement.empty, FiberId.None, level, () => "", Map.empty, Nil, ZTraceElement.empty, Map.empty)
        assertTrue(result == level.syslog.toString)
      }
    },
    test("fiberId") {
      val format = fiberId
      check(Gen.int, Gen.int) { (seq, time) =>
        val result = format.toLogger(
          ZTraceElement.empty,
          FiberId(seq, time),
          LogLevel.Info,
          () => "",
          Map.empty,
          Nil,
          ZTraceElement.empty,
          Map.empty
        )
        assertTrue(result == s"zio-fiber-$seq")
      }
    },
    test("annotation") {
      val format = annotation("test")
      check(Gen.string) { annotationValue =>
        val result = format.toLogger(
          ZTraceElement.empty,
          FiberId.None,
          LogLevel.Info,
          () => "",
          Map.empty,
          Nil,
          ZTraceElement.empty,
          Map("test" -> annotationValue)
        )
        assertTrue(result == s"test=$annotationValue")
      }
    },
    test("annotation (structured)") {
      val format = annotation(LogAnnotation.UserId)
      check(Gen.string) { annotationValue =>
        val result = format.toLogger(
          ZTraceElement.empty,
          FiberId.None,
          LogLevel.Info,
          () => "",
          Map(logContext -> LogContext.empty.annotate(LogAnnotation.UserId, annotationValue)),
          Nil,
          ZTraceElement.empty,
          Map.empty
        )
        assertTrue(result == s"user_id=$annotationValue")
      }
    },
    test("empty annotation") {
      val format = annotation("test")
      val result = format.toLogger(
        ZTraceElement.empty,
        FiberId.None,
        LogLevel.Info,
        () => "",
        Map.empty,
        Nil,
        ZTraceElement.empty,
        Map.empty
      )
      assertTrue(result == "")
    },
    test("enclosing class") {
      val format = enclosingClass
      val result = format
        .toLogger(implicitly[ZTraceElement], FiberId.None, LogLevel.Info, () => "", Map.empty, Nil, ZTraceElement.empty, Map.empty)
      assertTrue(result == "")
    } @@ TestAspect.ignore,
    test("string concat") {
      val format = text("a") + line + text("c")
      check(Gen.string) { line =>
        val result = format
          .toLogger(ZTraceElement.empty, FiberId.None, LogLevel.Info, () => line, Map.empty, Nil, ZTraceElement.empty, Map.empty)
        assertTrue(result == "a" + line + "c")
      }
    },
    test("spaced") {
      val format = line |-| text("c")
      check(Gen.string) { line =>
        val result = format
          .toLogger(ZTraceElement.empty, FiberId.None, LogLevel.Info, () => line, Map.empty, Nil, ZTraceElement.empty, Map.empty)
        assertTrue(result == line + " c")
      }
    },
    test("colored") {
      val format = line.color(LogColor.RED)
      check(Gen.string) { line =>
        val result = format
          .toLogger(ZTraceElement.empty, FiberId.None, LogLevel.Info, () => line, Map.empty, Nil, ZTraceElement.empty, Map.empty)
        assertTrue(result == LogColor.RED.ansi + line + LogColor.RESET.ansi)
      }
    },
    test("fixed") {
      val format = line.fixed(10)
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
        assertTrue(result.size == 10)
      }
    }
  )
}
