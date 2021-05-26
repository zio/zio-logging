package zio.logging

import zio.ZQueue
import zio.logging.LogAppender._
import zio.logging.LogFiltering.filterBy
import zio.test.Assertion._
import zio.test._

object LogFilteringSpec extends DefaultRunnableSpec {

  private def makeCtx(name: String, level: LogLevel): LogContext =
    LogContext.empty
      .annotate(LogAnnotation.Name, name.split('.').toList)
      .annotate(LogAnnotation.Level, level)

  private def testFilter(
    filter: (LogContext, => String) => Boolean,
    name: String,
    level: LogLevel,
    expectation: Assertion[Boolean]
  ): TestResult =
    assert(filter(makeCtx(name, level), ""))(expectation ?? s"$name with $level")

  val filter: (LogContext, => Any) => Boolean = filterBy(
    LogLevel.Debug,
    "a"     -> LogLevel.Info,
    "a.b.c" -> LogLevel.Warn,
    "e.f"   -> LogLevel.Error
  )

  override def spec: Spec[Environment, TestFailure[Nothing], TestSuccess] =
    suite("Log filtering")(
      test("can be built from list of nodes") {
        testFilter(filter, "x", LogLevel.Debug, isTrue) &&
        testFilter(filter, "a", LogLevel.Debug, isFalse) &&
        testFilter(filter, "a", LogLevel.Info, isTrue) &&
        testFilter(filter, "a.b", LogLevel.Debug, isFalse) &&
        testFilter(filter, "a.b", LogLevel.Info, isTrue) &&
        testFilter(filter, "a.b.c", LogLevel.Info, isFalse) &&
        testFilter(filter, "a.b.c", LogLevel.Warn, isTrue) &&
        testFilter(filter, "e", LogLevel.Debug, isTrue) &&
        testFilter(filter, "e.f", LogLevel.Debug, isFalse)
      },
      testM("can be applied on appenders") {
        for {
          queue           <- ZQueue.unbounded[String]
          baseAppender     = make(LogFormat.fromFunction((_, str) => str), (_, str) => queue.offer(str).unit)
          filteredAppender = baseAppender.withFilter(filter)
          _               <- filteredAppender.build.use { appender =>
                               println(appender)
                               appender.get.write(makeCtx("a.b.c", LogLevel.Debug), "a.b.c") *>
                                 appender.get.write(makeCtx("x", LogLevel.Debug), "x")
                             }
          result          <- queue.takeAll
        } yield assert(result)(equalTo(List("x")))
      }
    )
}
