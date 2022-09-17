package zio.logging

import zio.logging.test.TestService
import zio.test.ZTestLogger.LogEntry
import zio.test._
import zio.{ Cause, Chunk, FiberId, FiberRefs, LogLevel, LogSpan, Runtime, Trace, ZIO, ZLogger }

object LogFilterSpec extends ZIOSpecDefault {

  private def testFilter(
    filter: LogFilter,
    location: String,
    level: LogLevel,
    expectation: Assertion[Boolean]
  ): TestResult =
    assert(filter(Trace(location, "", 0), level, FiberRefs.empty, Map.empty))(expectation ?? s"$location with $level")

  private def testFilterAnnotation(
    filter: LogFilter,
    location: String,
    level: LogLevel,
    expectation: Assertion[Boolean]
  ): TestResult =
    assert(filter(Trace.empty, level, FiberRefs.empty, Map("name" -> location)))(
      expectation ?? s"$location with $level"
    )

  private def testLogger(
    logOutput: java.util.concurrent.atomic.AtomicReference[Chunk[LogEntry]],
    logFilter: LogFilter
  ) = {

    val logger = new ZLogger[String, Unit] {
      override def apply(
        trace: Trace,
        fiberId: FiberId,
        logLevel: LogLevel,
        message: () => String,
        cause: Cause[Any],
        context: FiberRefs,
        spans: List[LogSpan],
        annotations: Map[String, String]
      ): Unit = {
        val newEntry = LogEntry(trace, fiberId, logLevel, message, cause, context, spans, annotations)

        val oldState = logOutput.get

        if (!logOutput.compareAndSet(oldState, oldState :+ newEntry))
          apply(trace, fiberId, logLevel, message, cause, context, spans, annotations)
        else ()
      }
    }

    import LogFilter.ZLoggerLogFilterOps

    Runtime.removeDefaultLoggers >>> Runtime.addLogger(logger.filter(logFilter))
  }

  val spec: Spec[Environment, Any] = suite("LogFilterSpec")(
    test("log filtering by log level and name") {

      val filter: LogFilter = LogFilter.logLevelAndName(
        LogLevel.Debug,
        "a"     -> LogLevel.Info,
        "a.b.c" -> LogLevel.Warning,
        "e.f"   -> LogLevel.Error
      )

      testFilter(filter, "x.Exec.exec", LogLevel.Debug, Assertion.isTrue) &&
      testFilter(filter, "a.Exec.exec", LogLevel.Debug, Assertion.isFalse) &&
      testFilter(filter, "a.Exec.exec", LogLevel.Info, Assertion.isTrue) &&
      testFilter(filter, "a.b.Exec.exec", LogLevel.Debug, Assertion.isFalse) &&
      testFilter(filter, "a.b.Exec.exec", LogLevel.Info, Assertion.isTrue) &&
      testFilter(filter, "a.b.c.Exec.exec", LogLevel.Info, Assertion.isFalse) &&
      testFilter(filter, "a.b.c.Exec.exec", LogLevel.Warning, Assertion.isTrue) &&
      testFilter(filter, "e.Exec.exec", LogLevel.Debug, Assertion.isTrue) &&
      testFilter(filter, "e.f.Exec.exec", LogLevel.Debug, Assertion.isFalse)
    },
    test("log filtering by log level and name with annotation") {
      val loggerName: (Trace, FiberRefs, Map[String, String]) => String =
        (_, _, annotations) => annotations.getOrElse("name", "")

      val filter: LogFilter = LogFilter.logLevelAndName(
        LogLevel.Debug,
        loggerName,
        "a"     -> LogLevel.Info,
        "a.b.c" -> LogLevel.Warning,
        "e.f"   -> LogLevel.Error
      )

      testFilterAnnotation(filter, "x.Exec.exec", LogLevel.Debug, Assertion.isTrue) &&
      testFilterAnnotation(filter, "a.Exec.exec", LogLevel.Debug, Assertion.isFalse) &&
      testFilterAnnotation(filter, "a.Exec.exec", LogLevel.Info, Assertion.isTrue) &&
      testFilterAnnotation(filter, "a.b.Exec.exec", LogLevel.Debug, Assertion.isFalse) &&
      testFilterAnnotation(filter, "a.b.Exec.exec", LogLevel.Info, Assertion.isTrue) &&
      testFilterAnnotation(filter, "a.b.c.Exec.exec", LogLevel.Info, Assertion.isFalse) &&
      testFilterAnnotation(filter, "a.b.c.Exec.exec", LogLevel.Warning, Assertion.isTrue) &&
      testFilterAnnotation(filter, "e.Exec.exec", LogLevel.Debug, Assertion.isTrue) &&
      testFilterAnnotation(filter, "e.f.Exec.exec", LogLevel.Debug, Assertion.isFalse)
    },
    test("log with accept all filter") {
      val logOutputRef = new java.util.concurrent.atomic.AtomicReference[Chunk[LogEntry]](Chunk.empty)

      (for {
        _ <- ZIO.logDebug("debug")
        _ <- ZIO.logInfo("info")
        _ <- ZIO.logWarning("warning")
        _ <- ZIO.logError("error")
      } yield {
        val logOutput = logOutputRef.get()
        assertTrue(logOutput.length == 4) &&
        assertTrue(logOutput.map(_.message()) == Chunk("debug", "info", "warning", "error"))
      }).provideLayer(testLogger(logOutputRef, LogFilter.acceptAll))
    },
    test("logs filtered by log level") {
      val logOutputRef = new java.util.concurrent.atomic.AtomicReference[Chunk[LogEntry]](Chunk.empty)

      (for {
        _ <- ZIO.logDebug("debug")
        _ <- ZIO.logInfo("info")
        _ <- ZIO.logWarning("warning")
        _ <- ZIO.logError("error")
      } yield {
        val logOutput = logOutputRef.get()
        assertTrue(logOutput.length == 2) &&
        assertTrue(logOutput.map(_.message()) == Chunk("warning", "error"))
      }).provideLayer(testLogger(logOutputRef, LogFilter.logLevel(LogLevel.Warning)))
    },
    test("logs filtered by log level and name") {
      val logOutputRef = new java.util.concurrent.atomic.AtomicReference[Chunk[LogEntry]](Chunk.empty)

      val filter: LogFilter = LogFilter.logLevelAndName(
        LogLevel.Debug,
        "zio.logging"      -> LogLevel.Info,
        "zio.logging.test" -> LogLevel.Warning
      )

      (for {
        _ <- ZIO.logDebug("debug")
        _ <- ZIO.logInfo("info")
        _ <- ZIO.logWarning("warning")
        _ <- TestService.testDebug
        _ <- TestService.testInfo
        _ <- TestService.testWarning
        _ <- TestService.testError
      } yield {
        val logOutput = logOutputRef.get()
        assertTrue(logOutput.length == 4) &&
        assertTrue(logOutput.map(_.message()) == Chunk("info", "warning", "test warning", "test error"))
      }).provideLayer(testLogger(logOutputRef, filter))
    }
  )
}
