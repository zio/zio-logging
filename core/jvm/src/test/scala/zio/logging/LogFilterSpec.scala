package zio.logging

import zio.logging.test.TestService
import zio.test.ZTestLogger.LogEntry
import zio.test._
import zio.{
  Cause,
  Chunk,
  Config,
  ConfigProvider,
  FiberId,
  FiberRefs,
  LogLevel,
  LogSpan,
  Runtime,
  Trace,
  ZIO,
  ZIOAspect,
  ZLogger
}

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
        Map(loggerNameAnnotationKey -> location)
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
    suite("log filtering by log level and name")(
      test("simple paths") {
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
        testFilter(filter, "e.f.Exec.exec", LogLevel.Debug, Assertion.isFalse) &&
        testFilter(filter, "e.f.Exec.exec", LogLevel.Error, Assertion.isTrue)
      },
      test("any string pattern") {
        val filter: LogFilter[String] = LogFilter.logLevelByName(
          LogLevel.Debug,
          "a"     -> LogLevel.Info,
          "a.*.c" -> LogLevel.Warning,
          "e.f.*" -> LogLevel.Error
        )

        testFilter(filter, "x.Exec.exec", LogLevel.Debug, Assertion.isTrue) &&
        testFilter(filter, "a.Exec.exec", LogLevel.Debug, Assertion.isFalse) &&
        testFilter(filter, "a.Exec.exec", LogLevel.Info, Assertion.isTrue) &&
        testFilter(filter, "a.b.Exec.exec", LogLevel.Debug, Assertion.isFalse) &&
        testFilter(filter, "a.b2.Exec.exec", LogLevel.Debug, Assertion.isFalse) &&
        testFilter(filter, "a.b.Exec.exec", LogLevel.Info, Assertion.isTrue) &&
        testFilter(filter, "a.b.c.Exec.exec", LogLevel.Info, Assertion.isFalse) &&
        testFilter(filter, "a.b2.c.Exec.exec", LogLevel.Info, Assertion.isFalse) &&
        testFilter(filter, "a.b.c.Exec.exec", LogLevel.Warning, Assertion.isTrue) &&
        testFilter(filter, "a.b2.c.Exec.exec", LogLevel.Warning, Assertion.isTrue) &&
        testFilter(filter, "e.Exec.exec", LogLevel.Debug, Assertion.isTrue) &&
        testFilter(filter, "e.f.g.Exec.exec", LogLevel.Debug, Assertion.isFalse) &&
        testFilter(filter, "e.f.g.Exec.exec", LogLevel.Error, Assertion.isTrue)
      },
      test("any string and globstar patterns") {
        val filter: LogFilter[String] = LogFilter.logLevelByName(
          LogLevel.Debug,
          "a"       -> LogLevel.Info,
          "a.**.*y" -> LogLevel.Warning
        )

        testFilter(filter, "a.Exec.exec", LogLevel.Info, Assertion.isTrue) &&
        testFilter(filter, "a.y.Exec.exec", LogLevel.Info, Assertion.isFalse) &&
        testFilter(filter, "a.y.Exec.exec", LogLevel.Warning, Assertion.isTrue) &&
        testFilter(filter, "a.b.y.Exec.exec", LogLevel.Warning, Assertion.isTrue) &&
        testFilter(filter, "a.b.xy.Exec.exec", LogLevel.Warning, Assertion.isTrue) &&
        testFilter(filter, "a.b.xyz.Exec.exec", LogLevel.Debug, Assertion.isFalse) &&
        testFilter(filter, "a.b.xyz.Exec.exec", LogLevel.Info, Assertion.isTrue)
      },
      test("globstar pattern") {
        val filter: LogFilter[String] = LogFilter.logLevelByName(
          LogLevel.Debug,
          "a"      -> LogLevel.Info,
          "a.**.c" -> LogLevel.Warning,
          "e.f.**" -> LogLevel.Error
        )

        testFilter(filter, "x.Exec.exec", LogLevel.Debug, Assertion.isTrue) &&
        testFilter(filter, "a.Exec.exec", LogLevel.Debug, Assertion.isFalse) &&
        testFilter(filter, "a.Exec.exec", LogLevel.Info, Assertion.isTrue) &&
        testFilter(filter, "a.b.Exec.exec", LogLevel.Debug, Assertion.isFalse) &&
        testFilter(filter, "a.b2.Exec.exec", LogLevel.Debug, Assertion.isFalse) &&
        testFilter(filter, "a.b.Exec.exec", LogLevel.Info, Assertion.isTrue) &&
        testFilter(filter, "a.b.c.Exec.exec", LogLevel.Info, Assertion.isFalse) &&
        testFilter(filter, "a.b2.c.Exec.exec", LogLevel.Info, Assertion.isFalse) &&
        testFilter(filter, "a.b.b2.c.Exec.exec", LogLevel.Info, Assertion.isFalse) &&
        testFilter(filter, "a.b.c.Exec.exec", LogLevel.Warning, Assertion.isTrue) &&
        testFilter(filter, "a.b2.c.Exec.exec", LogLevel.Warning, Assertion.isTrue) &&
        testFilter(filter, "a.b.b2.c.Exec.exec", LogLevel.Warning, Assertion.isTrue) &&
        testFilter(filter, "e.Exec.exec", LogLevel.Debug, Assertion.isTrue) &&
        testFilter(filter, "e.f.Exec.exec", LogLevel.Debug, Assertion.isFalse) &&
        testFilter(filter, "e.f.g.Exec.exec", LogLevel.Debug, Assertion.isFalse) &&
        testFilter(filter, "e.f.g.Exec.exec", LogLevel.Error, Assertion.isTrue)
      }
    ),
    test("log filtering by log level and name with annotation") {

      val loggerName: LogGroup[Any, String] = LoggerNameExtractor.loggerNameAnnotationOrTrace.toLogGroup()

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
    test("log filtering by log level and name with annotation from config") {

      val configProvider = ConfigProvider.fromMap(
        Map(
          "logger/rootLevel"      -> LogLevel.Debug.label,
          "logger/mappings/a"     -> LogLevel.Info.label,
          "logger/mappings/a.b.c" -> LogLevel.Warning.label,
          "logger/mappings/e.f"   -> LogLevel.Error.label
        ),
        "/"
      )

      configProvider.load(LogFilter.LogLevelByNameConfig.config.nested("logger")).map { config =>
        val filter: LogFilter[String] = LogFilter.logLevelByName(config)

        testFilterAnnotation(filter, "x.Exec.exec", LogLevel.Debug, Assertion.isTrue) &&
        testFilterAnnotation(filter, "a.Exec.exec", LogLevel.Debug, Assertion.isFalse) &&
        testFilterAnnotation(filter, "a.Exec.exec", LogLevel.Info, Assertion.isTrue) &&
        testFilterAnnotation(filter, "a.b.Exec.exec", LogLevel.Debug, Assertion.isFalse) &&
        testFilterAnnotation(filter, "a.b.Exec.exec", LogLevel.Info, Assertion.isTrue) &&
        testFilterAnnotation(filter, "a.b.c.Exec.exec", LogLevel.Info, Assertion.isFalse) &&
        testFilterAnnotation(filter, "a.b.c.Exec.exec", LogLevel.Warning, Assertion.isTrue) &&
        testFilterAnnotation(filter, "e.Exec.exec", LogLevel.Debug, Assertion.isTrue) &&
        testFilterAnnotation(filter, "e.f.Exec.exec", LogLevel.Debug, Assertion.isFalse)
      }
    },
    test("log filtering by log level and name default config") {

      val configProvider = ConfigProvider.fromMap(Map.empty, "/")

      configProvider
        .load(LogFilter.LogLevelByNameConfig.config.nested("logger"))
        .map { config =>
          assertTrue(config.rootLevel == LogLevel.Info) && assertTrue(config.mappings.isEmpty)
        }
    },
    test("log filtering by log level and name config should fail on invalid values") {

      val configProvider = ConfigProvider.fromMap(Map("logger/rootLevel" -> "INVALID_LOG_LEVEL"), "/")

      configProvider
        .load(LogFilter.LogLevelByNameConfig.config.nested("logger"))
        .exit
        .map { e =>
          assert(e)(Assertion.failsWithA[Config.Error])
        }
    },
    test("log filtering by log level and name matcher with annotation") {

      val loggerName: LogGroup[Any, String] = LoggerNameExtractor.loggerNameAnnotationOrTrace.toLogGroup()

      val filter: LogFilter[String] = LogFilter.logLevelByGroup(
        LogLevel.Debug,
        loggerName,
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
        .cached

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
      ) &&
      check(
        Seq(
          "a"               -> LogLevel.Warning,
          "a"               -> LogLevel.Info,
          "**"              -> LogLevel.Info,
          "*"               -> LogLevel.Info,
          "a.**.c.Service1" -> LogLevel.Warning,
          "a.b.c.Service1"  -> LogLevel.Warning,
          "a.*.c.Service1"  -> LogLevel.Warning,
          "a.b.c"           -> LogLevel.Error,
          "a.b.d"           -> LogLevel.Debug,
          "e.f"             -> LogLevel.Error,
          "e.*.g.*.i"       -> LogLevel.Error,
          "e.*.g.h.*"       -> LogLevel.Error,
          "e.f.*.h"         -> LogLevel.Error
        ),
        Seq(
          "e.f.*.h"         -> LogLevel.Error,
          "e.f"             -> LogLevel.Error,
          "e.*.g.h.*"       -> LogLevel.Error,
          "e.*.g.*.i"       -> LogLevel.Error,
          "a.b.d"           -> LogLevel.Debug,
          "a.b.c.Service1"  -> LogLevel.Warning,
          "a.b.c"           -> LogLevel.Error,
          "a.*.c.Service1"  -> LogLevel.Warning,
          "a.**.c.Service1" -> LogLevel.Warning,
          "a"               -> LogLevel.Info,
          "a"               -> LogLevel.Warning,
          "*"               -> LogLevel.Info,
          "**"              -> LogLevel.Info
        )
      )
    },
    test("log filters by log level and name from same configuration should be equal") {

      val configProvider = ConfigProvider.fromMap(
        Map(
          "logger/rootLevel"      -> LogLevel.Debug.label,
          "logger/mappings/a"     -> LogLevel.Info.label,
          "logger/mappings/a.b.c" -> LogLevel.Warning.label,
          "logger/mappings/e.f"   -> LogLevel.Error.label
        ),
        "/"
      )

      import zio.prelude._

      for {
        f1 <- configProvider
                .load(LogFilter.LogLevelByNameConfig.config.nested("logger"))
                .map(LogFilter.logLevelByName)
        f2 <- configProvider
                .load(LogFilter.LogLevelByNameConfig.config.nested("logger"))
                .map(LogFilter.logLevelByName)
        f3 <- configProvider
                .load(LogFilter.LogLevelByNameConfig.config.nested("logger"))
                .map(LogFilter.logLevelByName)
                .map(_.cached)
        f4 <- configProvider
                .load(LogFilter.LogLevelByNameConfig.config.nested("logger"))
                .map(LogFilter.logLevelByName)
                .map(_.cached)
      } yield assertTrue(f1 === f2, f3 === f4)
    },
    test("and") {

      val filter = LogFilter.causeNonEmpty.and(LogFilter.logLevel(LogLevel.Info))

      def testFilter(level: LogLevel, cause: Cause[_], expected: Boolean) =
        assertTrue(
          filter(
            Trace.empty,
            FiberId.None,
            level,
            () => "",
            cause,
            FiberRefs.empty,
            List.empty,
            Map.empty
          ) == expected
        )

      testFilter(LogLevel.Info, Cause.fail("fail"), true) && testFilter(
        LogLevel.Info,
        Cause.empty,
        false
      ) && testFilter(LogLevel.Debug, Cause.fail("fail"), false)
    },
    test("or") {

      val filter = LogFilter.causeNonEmpty.or(LogFilter.logLevel(LogLevel.Info))

      def testFilter(level: LogLevel, cause: Cause[_], expected: Boolean) =
        assertTrue(
          filter(
            Trace.empty,
            FiberId.None,
            level,
            () => "",
            cause,
            FiberRefs.empty,
            List.empty,
            Map.empty
          ) == expected
        )

      testFilter(LogLevel.Info, Cause.fail("fail"), true) && testFilter(LogLevel.Info, Cause.empty, true) && testFilter(
        LogLevel.Debug,
        Cause.fail("fail"),
        true
      ) && testFilter(LogLevel.Debug, Cause.empty, false)
    },
    test("not") {

      val filter = LogFilter.causeNonEmpty.and(LogFilter.logLevel(LogLevel.Info)).not

      def testFilter(level: LogLevel, cause: Cause[_], expected: Boolean) =
        assertTrue(
          filter(
            Trace.empty,
            FiberId.None,
            level,
            () => "",
            cause,
            FiberRefs.empty,
            List.empty,
            Map.empty
          ) == expected
        )

      testFilter(LogLevel.Info, Cause.fail("fail"), false) && testFilter(
        LogLevel.Info,
        Cause.empty,
        true
      ) && testFilter(LogLevel.Debug, Cause.fail("fail"), true)
    },
    test("cached") {
      val logOutputRef = new java.util.concurrent.atomic.AtomicReference[Chunk[LogEntry]](Chunk.empty)

      val filter = LogFilter
        .logLevelByGroup(
          LogLevel.Info,
          LogGroup.loggerName,
          "zio.logger1"      -> LogLevel.Debug,
          "zio.logging.test" -> LogLevel.Warning
        )
        .cached
        .asInstanceOf[LogFilter.CachedFilter[String]]

      (for {
        _   <- ZIO.logDebug("debug") @@ ZIOAspect.annotated(loggerNameAnnotationKey, "zio.logger1")
        res1 = filter.cache.get(List("zio", "logger1") -> LogLevel.Debug)
        _   <- ZIO.logDebug("debug") @@ ZIOAspect.annotated(loggerNameAnnotationKey, "zio.logger2")
        res2 = filter.cache.get(List("zio", "logger2") -> LogLevel.Debug)
      } yield assertTrue(res1 == true, res2 == false)).provideLayer(testLogger(logOutputRef, filter))
    }
  )
}
