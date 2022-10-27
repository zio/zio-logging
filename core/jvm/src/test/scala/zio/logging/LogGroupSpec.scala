package zio.logging

import zio.test._
import zio.{ Cause, FiberId, FiberRefs, LogLevel, Trace }

object LogGroupSpec extends ZIOSpecDefault {
  val spec: Spec[Environment, Any] = suite("LogGroupSpec")(
    test("level") {
      val group = LogGroup.logLevel
      check(Gen.elements(LogLevel.Info, LogLevel.Warning, LogLevel.Error, LogLevel.Debug)) { level =>
        val result = group(
          Trace.empty,
          FiberId.None,
          level,
          () => "",
          Cause.empty,
          FiberRefs.empty,
          Nil,
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
          FiberId.None,
          LogLevel.Info,
          () => "",
          Cause.empty,
          FiberRefs.empty,
          Nil,
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
            FiberId.None,
            level,
            () => "",
            Cause.empty,
            FiberRefs.empty,
            Nil,
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
            FiberId.None,
            level,
            () => "",
            Cause.empty,
            FiberRefs.empty,
            Nil,
            Map.empty
          )
          assertTrue(result == (value -> level))
      }
    },
    test("related") {
      val group = LogGroup.loggerName ++ LogGroup.logLevel
      check(Gen.alphaNumericString, Gen.elements(LogLevel.Info, LogLevel.Warning, LogLevel.Error, LogLevel.Debug)) {
        (value, level) =>
          val result = group.related(
            Trace.apply(value, "", 0),
            FiberId.None,
            level,
            () => "",
            Cause.empty,
            FiberRefs.empty,
            Nil,
            Map.empty
          )((value -> level))
          assertTrue(result)
      }
    }
  )
}
