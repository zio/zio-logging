package zio.logging

import zio.test._
import zio.{ FiberRefs, LogLevel, Trace }

object LogGroupSpec extends ZIOSpecDefault {
  val spec: Spec[Environment, Any] = suite("LogGroupSpec")(
    test("level") {
      val group = LogGroup.level
      check(Gen.elements(LogLevel.Info, LogLevel.Warning, LogLevel.Error, LogLevel.Debug)) { level =>
        val result = group(
          Trace.empty,
          level,
          FiberRefs.empty,
          Map.empty
        )
        assertTrue(result == level)
      }
    },
    test("loggerName") {
      val group = LogGroup.loggerName
      check(Gen.string) { value =>
        val result = group(
          Trace.apply(value, "", 0),
          LogLevel.Info,
          FiberRefs.empty,
          Map.empty
        )
        assertTrue(result == value)
      }
    },
    test("loggerNameAndLevel") {
      val group = LogGroup.loggerNameAndLevel
      check(Gen.string, Gen.elements(LogLevel.Info, LogLevel.Warning, LogLevel.Error, LogLevel.Debug)) {
        (value, level) =>
          val result = group(
            Trace.apply(value, "", 0),
            level,
            FiberRefs.empty,
            Map.empty
          )
          assertTrue(result == (value -> level))
      }
    }
  )
}
