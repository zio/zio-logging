package zio.logging

import zio.test._
import LogFormat.{ level, line, _ }
import zio.{ FiberId, LogLevel, ZTraceElement }

object LogFormatSpec extends DefaultRunnableSpec {
  val spec = suite("log format")(
    test("line") {
      val format = line
      check(Gen.string) { line =>
        val result = format.toLogger(ZTraceElement.empty, FiberId.None, LogLevel.Info, () => line, Map.empty, Nil)
        assertTrue(result == line)
      }
    },
    test("level") {
      val format = level
      check(Gen.elements(LogLevel.Info, LogLevel.Warning, LogLevel.Error, LogLevel.Debug)) { level =>
        val result = format.toLogger(ZTraceElement.empty, FiberId.None, level, () => "", Map.empty, Nil)
        assertTrue(result == level.label)
      }
    },
    test("level_value") {
      val format = level_value
      check(Gen.elements(LogLevel.Info, LogLevel.Warning, LogLevel.Error, LogLevel.Debug)) { level =>
        val result = format.toLogger(ZTraceElement.empty, FiberId.None, level, () => "", Map.empty, Nil)
        assertTrue(result == level.syslog.toString)
      }
    },
    test("fiberId") {
      val format = fiberNumber
      check(Gen.long, Gen.long) { (time, seq) =>
        val result = format.toLogger(ZTraceElement.empty, FiberId(time, seq), LogLevel.Info, () => "", Map.empty, Nil)
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
          Nil
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
        Nil
      )
      assertTrue(result == "")
    },
    test("enclosing class") {
      val format = enclosingClass
      val result = format.toLogger(implicitly[ZTraceElement], FiberId.None, LogLevel.Info, () => "", Map.empty, Nil)
      assertTrue(result == "")
    } @@ TestAspect.ignore,
    test("string concat") {
      val format = string("a") + line + string("c")
      check(Gen.string) { line =>
        val result = format.toLogger(ZTraceElement.empty, FiberId.None, LogLevel.Info, () => line, Map.empty, Nil)
        assertTrue(result == "a" + line + "c")
      }
    },
    test("spaced") {
      val format = line |-| string("c")
      check(Gen.string) { line =>
        val result = format.toLogger(ZTraceElement.empty, FiberId.None, LogLevel.Info, () => line, Map.empty, Nil)
        assertTrue(result == line + " c")
      }
    },
    test("colored") {
      val format = line.color(LogColor.RED)
      check(Gen.string) { line =>
        val result = format.toLogger(ZTraceElement.empty, FiberId.None, LogLevel.Info, () => line, Map.empty, Nil)
        assertTrue(result == LogColor.RED.ansi + line + LogColor.RESET.ansi)
      }
    },
    test("fixed") {
      val format = line.fixed(10)
      check(Gen.string) { line =>
        val result = format.toLogger(ZTraceElement.empty, FiberId.None, LogLevel.Info, () => line, Map.empty, Nil)
        assertTrue(result.size == 10)
      }
    }
  )
}
