package zio.logging

import zio.test._
import zio.{ LogLevel, Trace }

object LogFilteringSpec extends ZIOSpecDefault {

  val filter: (zio.Trace, LogLevel) => Boolean = LogFiltering.filterBy(
    LogLevel.Debug,
    "a"     -> LogLevel.Info,
    "a.b.c" -> LogLevel.Warning,
    "e.f"   -> LogLevel.Error
  )

  private def testFilter(
    filter: (Trace, LogLevel) => Boolean,
    location: String,
    level: LogLevel,
    expectation: Assertion[Boolean]
  ): TestResult =
    assert(filter(Trace(location, "", 0), level))(expectation ?? s"$location with $level")

  val spec: Spec[Environment, Any] = suite("LogFilteringSpec")(
    test("can be built from list of nodes") {
      testFilter(filter, "x.Exec.exec", LogLevel.Debug, Assertion.isTrue) &&
      testFilter(filter, "a.Exec.exec", LogLevel.Debug, Assertion.isFalse) &&
      testFilter(filter, "a.Exec.exec", LogLevel.Info, Assertion.isTrue) &&
      testFilter(filter, "a.b.Exec.exec", LogLevel.Debug, Assertion.isFalse) &&
      testFilter(filter, "a.b.Exec.exec", LogLevel.Info, Assertion.isTrue) &&
      testFilter(filter, "a.b.c.Exec.exec", LogLevel.Info, Assertion.isFalse) &&
      testFilter(filter, "a.b.c.Exec.exec", LogLevel.Warning, Assertion.isTrue) &&
      testFilter(filter, "e.Exec.exec", LogLevel.Debug, Assertion.isTrue) &&
      testFilter(filter, "e.f.Exec.exec", LogLevel.Debug, Assertion.isFalse)
    }
  )
}
