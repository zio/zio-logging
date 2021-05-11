package zio.logging

import zio.ZQueue
import zio.logging.LogAppender._
import zio.logging.LogFiltering.filterBy
import zio.test.Assertion._
import zio.test._
import zio.Has
import zio.blocking.Blocking
import zio.clock.Clock
import zio.random.Random
import zio.test.environment.{ Live, TestClock, TestConsole, TestRandom, TestSystem }

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

  override def spec: Spec[Has[Annotations.Service] with Has[Live.Service] with Has[Sized.Service] with Has[
    TestClock.Service
  ] with Has[TestConfig.Service] with Has[TestConsole.Service] with Has[TestRandom.Service] with Has[
    TestSystem.Service
  ] with Has[Clock.Service] with Has[zio.console.Console.Service] with Has[zio.system.System.Service] with Has[
    Random.Service
  ] with Has[Blocking.Service], TestFailure[Any], TestSuccess] =
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
