package zio.logging

import zio.test._
import zio.{ FiberId, LogLevel, ZTraceElement }

import LogFormat.{ level, line, _ }

object LogFormatSpec extends DefaultRunnableSpec {
  val spec: ZSpec[Environment, Failure] = suite("log format")(
    test("line") {
      val format = line
      check(Gen.string) { line =>
        val result = format.toLogger(ZTraceElement.empty, FiberId.None, LogLevel.Info, () => line, Map.empty, Nil, ZTraceElement.empty)
        assertTrue(result == line)
      }
    },
    test("level") {
      val format = level
      check(Gen.elements(LogLevel.Info, LogLevel.Warning, LogLevel.Error, LogLevel.Debug)) { level =>
        val result = format.toLogger(ZTraceElement.empty, FiberId.None, level, () => "", Map.empty, Nil, ZTraceElement.empty)
        assertTrue(result == level.label)
      }
    },
    test("level_value") {
      val format = level_value
      check(Gen.elements(LogLevel.Info, LogLevel.Warning, LogLevel.Error, LogLevel.Debug)) { level =>
        val result = format.toLogger(ZTraceElement.empty, FiberId.None, level, () => "", Map.empty, Nil, ZTraceElement.empty)
        assertTrue(result == level.syslog.toString)
      }
    },
    test("fiberId") {
      val format = fiberNumber
      check(Gen.int, Gen.int) { (seq, time) =>
        val result = format.toLogger(ZTraceElement.empty, FiberId(seq, time), LogLevel.Info, () => "", Map.empty, Nil, ZTraceElement.empty)
        assertTrue(result == s"zio-fiber-<$seq>")
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
          Map(logAnnotation -> Map("test" -> annotationValue)),
          Nil,
          ZTraceElement.empty
        )
        assertTrue(result == annotationValue)
      }
    },
    test("empty annotation") {
      val format = annotation("test")
      val result = format.toLogger(
        ZTraceElement.empty,
        FiberId.None,
        LogLevel.Info,
        () => "",
        Map(logAnnotation -> Map.empty),
        Nil,
        ZTraceElement.empty
      )
      assertTrue(result == "")
    },
    test("enclosing class") {
      val format = enclosingClass
      val result = format.toLogger(implicitly[ZTraceElement], FiberId.None, LogLevel.Info, () => "", Map.empty, Nil, ZTraceElement.empty)
      assertTrue(result == "")
    } @@ TestAspect.ignore,
    test("string concat") {
      val format = string("a") + line + string("c")
      check(Gen.string) { line =>
        val result = format.toLogger(ZTraceElement.empty, FiberId.None, LogLevel.Info, () => line, Map.empty, Nil, ZTraceElement.empty)
        assertTrue(result == "a" + line + "c")
      }
    },
    test("spaced") {
      val format = line |-| string("c")
      check(Gen.string) { line =>
        val result = format.toLogger(ZTraceElement.empty, FiberId.None, LogLevel.Info, () => line, Map.empty, Nil, ZTraceElement.empty)
        assertTrue(result == line + " c")
      }
    },
    test("colored") {
      val format = line.color(LogColor.RED)
      check(Gen.string) { line =>
        val result = format.toLogger(ZTraceElement.empty, FiberId.None, LogLevel.Info, () => line, Map.empty, Nil, ZTraceElement.empty)
        assertTrue(result == LogColor.RED.ansi + line + LogColor.RESET.ansi)
      }
    },
    test("fixed") {
      val format = line.fixed(10)
      check(Gen.string) { line =>
        val result = format.toLogger(ZTraceElement.empty, FiberId.None, LogLevel.Info, () => line, Map.empty, Nil, ZTraceElement.empty)
        assertTrue(result.size == 10)
      }
    }
  )
}
