package zio.logging

import zio.test._
import zio.{ FiberRefs, LogLevel, Trace }

object LogGroupSpec extends ZIOSpecDefault {
  val spec: Spec[Environment, Any] = suite("LogGroupSpec")(
    test("level") {
      val group = LogGroup.logLevel
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
      check(Gen.alphaNumericString) { value =>
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
      check(Gen.alphaNumericString, Gen.elements(LogLevel.Info, LogLevel.Warning, LogLevel.Error, LogLevel.Debug)) {
        (value, level) =>
          val result = group(
            Trace.apply(value, "", 0),
            level,
            FiberRefs.empty,
            Map.empty
          )
          assertTrue(result == (value -> level))
      }
    },
    test("zipWith") {
      val group = LogGroup.loggerName.zipWith(LogGroup.logLevel)(_ -> _)
      check(Gen.alphaNumericString, Gen.elements(LogLevel.Info, LogLevel.Warning, LogLevel.Error, LogLevel.Debug)) {
        (value, level) =>
          val result = group(
            Trace.apply(value, "", 0),
            level,
            FiberRefs.empty,
            Map.empty
          )
          assertTrue(result == (value -> level))
      }
    },
    test("++") {
      val group = LogGroup.loggerName ++ LogGroup.logLevel
      check(Gen.alphaNumericString, Gen.elements(LogLevel.Info, LogLevel.Warning, LogLevel.Error, LogLevel.Debug)) {
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
