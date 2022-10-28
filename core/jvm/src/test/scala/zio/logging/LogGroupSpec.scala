package zio.logging

import zio.test._
import zio.{ Cause, FiberId, FiberRefs, LogLevel, Trace }

object LogGroupSpec extends ZIOSpecDefault {
  val spec: Spec[Environment, Any] = suite("LogGroupSpec")(
    test("logLevel") {
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
    test("contramap") {
      val group = LogGroup[String, String]((_, _, _, line, _, _, _, _) => line()).contramap[Long](_.toString)
      check(Gen.long) { value =>
        val result = group(
          Trace.empty,
          FiberId.None,
          LogLevel.Info,
          () => value,
          Cause.empty,
          FiberRefs.empty,
          Nil,
          Map.empty
        )
        assertTrue(result == value.toString)
      }
    },
    test("map") {
      val group = LogGroup.loggerName.map("PREFIX_" + _)
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
        assertTrue(result == ("PREFIX_" + value))
      }
    },
    test("zipWith") {
      val group = LogGroup.loggerName.zipWith(LogGroup.logLevel)(_ -> _)
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
    }
  )
}
