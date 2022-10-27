package zio.logging

import zio.logging.test.TestService
import zio.test.ZTestLogger.LogEntry
import zio.test._
import zio.{ Cause, Chunk, FiberId, FiberRefs, LogLevel, LogSpan, Runtime, Trace, ZIO, ZLogger }

object LogFilterSpec extends ZIOSpecDefault {

  private def testFilter(
    filter: LogFilter[String],
    location: String,
    level: LogLevel,
    expectation: Assertion[Boolean]
  ): TestResult =
    assert(
      filter(Trace(location, "", 0), FiberId.None, level, () => "", Cause.empty, FiberRefs.empty, List.empty, Map.empty)
    )(expectation ?? s"$location with $level")

  private def testFilterAnnotation(
    filter: LogFilter[String],
    location: String,
    level: LogLevel,
    expectation: Assertion[Boolean]
  ): TestResult =
    assert(
      filter(
        Trace.empty,
        FiberId.None,
        level,
        () => "",
        Cause.empty,
        FiberRefs.empty,
        List.empty,
        Map("name" -> location)
      )
    )(
      expectation ?? s"$location with $level"
    )

  private def testLogger(
    logOutput: java.util.concurrent.atomic.AtomicReference[Chunk[LogEntry]],
    logFilter: LogFilter[String]
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

    Runtime.removeDefaultLoggers >>> Runtime.addLogger(logFilter.filter(logger))
  }

  private def testLoggerWithFilter(filter: LogFilter[String], expected: Chunk[String]) = {
    val logOutputRef = new java.util.concurrent.atomic.AtomicReference[Chunk[LogEntry]](Chunk.empty)
    (for {
      _ <- ZIO.logDebug("debug")
      _ <- ZIO.logInfo("info")
      _ <- ZIO.logWarning("warning")
      _ <- ZIO.logError("error")
    } yield {
      val logOutput = logOutputRef.get()
      assertTrue(logOutput.map(_.message()) == expected)
    }).provideLayer(testLogger(logOutputRef, filter))
  }

  val spec: Spec[Environment, Any] = suite("LogFilterSpec")(
    test("log filtering by log level and name") {

      val filter: LogFilter[String] = LogFilter.logLevelByName(
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

      val loggerName: LogGroup[Any, String] = LoggerNameExtractor.annotation("name").toLogGroup()

      val filter: LogFilter[String] = LogFilter.logLevelByGroup(
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
    test("log filtering by log level and name matcher with annotation") {

      val loggerName: LogGroup[Any, String] = LoggerNameExtractor.annotation("name").toLogGroup()

      val filter: LogFilter[String] = LogFilter.logLevelByGroup(
        LogLevel.Debug,
        loggerName,
        LogGroupRelation.stringStartWith,
        "a.b.c" -> LogLevel.Warning,
        "a"     -> LogLevel.Info,
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
      testLoggerWithFilter(LogFilter.acceptAll, Chunk("debug", "info", "warning", "error"))
    },
    test("log nothing with accept all filter negated") {
      testLoggerWithFilter(!LogFilter.acceptAll, Chunk.empty)
    },
    test("logs filtered by log level") {
      testLoggerWithFilter(LogFilter.logLevel(LogLevel.Warning), Chunk("warning", "error"))
    },
    test("logs filtered by accept all or log level") {
      testLoggerWithFilter(
        LogFilter.acceptAll || LogFilter.logLevel(LogLevel.Warning),
        Chunk("debug", "info", "warning", "error")
      )
    },
    test("logs filtered by accept all and log level") {
      testLoggerWithFilter(LogFilter.acceptAll && LogFilter.logLevel(LogLevel.Warning), Chunk("warning", "error"))
    },
    test("logs filtered by log level and name") {
      val logOutputRef = new java.util.concurrent.atomic.AtomicReference[Chunk[LogEntry]](Chunk.empty)

      val filter: LogFilter[String] = LogFilter.logLevelByName(
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
        assertTrue(logOutput.map(_.message()) == Chunk("info", "warning", "test warning", "test error"))
      }).provideLayer(testLogger(logOutputRef, filter))
    },
    test("logs filtered by log level and name") {
      val logOutputRef = new java.util.concurrent.atomic.AtomicReference[Chunk[LogEntry]](Chunk.empty)

      val filter: LogFilter[String] = LogFilter
        .logLevelByGroup(
          LogLevel.Debug,
          LogGroup.loggerName,
          "zio.logging"      -> LogLevel.Info,
          "zio.logging.test" -> LogLevel.Warning
        )
        .cachedBy(LogGroup.loggerNameAndLevel)

      (for {
        _ <- ZIO.logDebug("debug")
        _ <- ZIO.logInfo("info")
        _ <- ZIO.logWarning("warning")
        _ <- TestService.testDebug
        _ <- TestService.testInfo
        _ <- TestService.testWarning
        _ <- TestService.testError
        _ <- ZIO.logDebug("debug")
        _ <- ZIO.logInfo("info")
        _ <- ZIO.logWarning("warning")
        _ <- TestService.testDebug
        _ <- TestService.testInfo
        _ <- TestService.testWarning
        _ <- TestService.testError
      } yield {
        val logOutput = logOutputRef.get()
        assertTrue(
          logOutput.map(_.message()) == Chunk(
            "info",
            "warning",
            "test warning",
            "test error",
            "info",
            "warning",
            "test warning",
            "test error"
          )
        )
      }).provideLayer(testLogger(logOutputRef, filter))
    },
    test("nameLevelOrdering") {
      def check(input: Seq[(String, LogLevel)], expected: Seq[(String, LogLevel)]) = {
        val in  = input.map(LogFilter.splitNameByDotAndLevel.tupled).sorted(LogFilter.nameLevelOrdering)
        val exp = expected.map(LogFilter.splitNameByDotAndLevel.tupled)
        assertTrue(in == exp)
      }

      check(
        Seq("a"   -> LogLevel.Info, "a.b.c"  -> LogLevel.Warning, "e.f" -> LogLevel.Error),
        Seq("e.f" -> LogLevel.Error, "a.b.c" -> LogLevel.Warning, "a"   -> LogLevel.Info)
      ) &&
      check(
        Seq(
          "a"              -> LogLevel.Warning,
          "a"              -> LogLevel.Info,
          "a.b.c.Service1" -> LogLevel.Warning,
          "a.b.c"          -> LogLevel.Error,
          "a.b.d"          -> LogLevel.Debug,
          "e.f"            -> LogLevel.Error
        ),
        Seq(
          "e.f"            -> LogLevel.Error,
          "a.b.d"          -> LogLevel.Debug,
          "a.b.c.Service1" -> LogLevel.Warning,
          "a.b.c"          -> LogLevel.Error,
          "a"              -> LogLevel.Info,
          "a"              -> LogLevel.Warning
        )
      )
    }
  )
}
