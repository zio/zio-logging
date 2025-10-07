package zio.logging.slf4j.bridge

import org.slf4j.MarkerFactory
import org.slf4j.impl.StaticMarkerBinder
import zio.logging.LogFilter
import zio.test.{ Spec, _ }
import zio.{ Cause, Chunk, ConfigProvider, LogLevel, Runtime, ZIO, ZIOAspect }

object Slf4jBridgeSpec extends ZIOSpecDefault {

  final case class LogEntry(
    span: List[String],
    level: LogLevel,
    annotations: Map[String, String],
    message: String,
    cause: Cause[Any]
  )

  override def spec: Spec[Any, Throwable] =
    suite("Slf4jBridge")(
      test("parallel init") {
        for {
          _ <-
            ZIO.foreachPar((1 to 5).toList) { _ =>
              ZIO
                .succeed(org.slf4j.LoggerFactory.getLogger("SLF4J-LOGGER").warn("Test {}!", "WARNING"))
                .provide(Slf4jBridge.initialize)
            }
        } yield assertCompletes
      },
      test("Implements Logger#getName") {
        for {
          logger <- ZIO.attempt(org.slf4j.LoggerFactory.getLogger("zio.test.logger"))
        } yield assertTrue(logger.getName == "zio.test.logger")
      }.provide(Slf4jBridge.initialize),
      test("implements MarkerFactoryBinder") {
        for {
          markerFactory <- ZIO.attempt(MarkerFactory.getIMarkerFactory)
        } yield assertTrue(markerFactory.eq(StaticMarkerBinder.getSingleton.getMarkerFactory))
      }.provide(Slf4jBridge.initialize),
      test("logs through slf4j") {
        val testFailure = new RuntimeException("test error")
        for {
          _      <- (for {
                      logger <- ZIO.attempt(org.slf4j.LoggerFactory.getLogger("test.logger"))
                      _      <- ZIO.logSpan("span")(ZIO.attempt(logger.debug("test debug message"))) @@ ZIOAspect
                                  .annotated("trace_id", "tId")
                      _      <- ZIO.attempt(logger.warn("hello {}", "world")) @@ ZIOAspect.annotated("user_id", "uId")
                      _      <- ZIO.attempt(logger.warn("{}..{}..{} ... go!", "3", "2", "1"))
                      _      <- ZIO.attempt(logger.warn("warn cause", testFailure))
                      _      <- ZIO.attempt(logger.error("error", testFailure))
                      _      <- ZIO.attempt(logger.error("error", null))
                    } yield ()).exit
          output <- ZTestLogger.logOutput
          lines   = output.map { logEntry =>
                      LogEntry(
                        logEntry.spans.map(_.label),
                        logEntry.logLevel,
                        logEntry.annotations,
                        logEntry.message(),
                        logEntry.cause
                      )
                    }
        } yield assertTrue(
          lines == Chunk(
            LogEntry(
              List("test.logger", "span"),
              LogLevel.Debug,
              Map(zio.logging.loggerNameAnnotationKey -> "test.logger", "trace_id" -> "tId"),
              "test debug message",
              Cause.empty
            ),
            LogEntry(
              List("test.logger"),
              LogLevel.Warning,
              Map(zio.logging.loggerNameAnnotationKey -> "test.logger", "user_id" -> "uId"),
              "hello world",
              Cause.empty
            ),
            LogEntry(
              List("test.logger"),
              LogLevel.Warning,
              Map(zio.logging.loggerNameAnnotationKey -> "test.logger"),
              "3..2..1 ... go!",
              Cause.empty
            ),
            LogEntry(
              List("test.logger"),
              LogLevel.Warning,
              Map(zio.logging.loggerNameAnnotationKey -> "test.logger"),
              "warn cause",
              Cause.die(testFailure)
            ),
            LogEntry(
              List("test.logger"),
              LogLevel.Error,
              Map(zio.logging.loggerNameAnnotationKey -> "test.logger"),
              "error",
              Cause.die(testFailure)
            ),
            LogEntry(
              List("test.logger"),
              LogLevel.Error,
              Map(zio.logging.loggerNameAnnotationKey -> "test.logger"),
              "error",
              Cause.empty
            )
          )
        )
      }.provide(Slf4jBridge.initialize),
      test("logs through slf4j without fiber ref propagation") {
        for {
          _      <- (for {
                      logger <- ZIO.attempt(org.slf4j.LoggerFactory.getLogger("test.logger"))
                      _      <- ZIO.attempt(logger.debug("test debug message")) @@ ZIOAspect.annotated("trace_id", "tId")
                      _      <- ZIO.attempt(logger.warn("hello {}", "world")) @@ ZIOAspect.annotated("user_id", "uId")
                    } yield ()).exit
          output <- ZTestLogger.logOutput
          lines   = output.map { logEntry =>
                      LogEntry(
                        logEntry.spans.map(_.label),
                        logEntry.logLevel,
                        logEntry.annotations,
                        logEntry.message(),
                        logEntry.cause
                      )
                    }
        } yield assertTrue(
          lines == Chunk(
            LogEntry(
              List("test.logger"),
              LogLevel.Debug,
              Map(zio.logging.loggerNameAnnotationKey -> "test.logger"),
              "test debug message",
              Cause.empty
            ),
            LogEntry(
              List("test.logger"),
              LogLevel.Warning,
              Map(zio.logging.loggerNameAnnotationKey -> "test.logger"),
              "hello world",
              Cause.empty
            )
          )
        )
      }.provide(Slf4jBridge.initializeWithoutFiberRefPropagation),
      test("logs through slf4j with filter") {
        filterTest()
      }.provide(
        Slf4jBridge.init(
          LogFilter.logLevelByName(
            LogLevel.Debug,
            "test.logger"      -> LogLevel.Info,
            "test.test.logger" -> LogLevel.Warning
          )
        )
      ),
      test("logs through slf4j with filter from config") {
        filterTest()
      }.provide {
        val configProvider: ConfigProvider = ConfigProvider.fromMap(
          Map(
            "logger/filter/rootLevel"                 -> "DEBUG",
            "logger/filter/mappings/test.logger"      -> "INFO",
            "logger/filter/mappings/test.test.logger" -> "WARN"
          ),
          "/"
        )
        Runtime.setConfigProvider(configProvider) >>> Slf4jBridge.init()
      },
      test("logs through slf4j with filter from config with disabled loggerNameLogSpan and fiberRefPropagation") {
        filterTest(Slf4jBridgeConfig(loggerNameLogSpan = false, fiberRefPropagation = false))
      }.provide {
        val configProvider: ConfigProvider = ConfigProvider.fromMap(
          Map(
            "logger/filter/rootLevel"                 -> "DEBUG",
            "logger/filter/mappings/test.logger"      -> "INFO",
            "logger/filter/mappings/test.test.logger" -> "WARN",
            "logger/bridge/loggerNameLogSpan"         -> "false",
            "logger/bridge/fiberRefPropagation"       -> "false"
          ),
          "/"
        )
        Runtime.setConfigProvider(configProvider) >>> Slf4jBridge.init()
      }
    ) @@ TestAspect.sequential

  def filterTest(config: Slf4jBridgeConfig = Slf4jBridgeConfig.default): ZIO[Any, Nothing, TestResult] =
    for {
      _      <- (for {
                  logger1 <- ZIO.attempt(org.slf4j.LoggerFactory.getLogger("test.abc"))
                  logger2 <- ZIO.attempt(org.slf4j.LoggerFactory.getLogger("test.logger.def"))
                  logger3 <- ZIO.attempt(org.slf4j.LoggerFactory.getLogger("test.test.logger.xyz"))
                  _       <- ZIO.attempt(logger1.debug("test debug message")) @@ ZIOAspect.annotated("user_id", "uId")
                  _       <- ZIO.attempt(logger1.warn("test warn message")) @@ ZIOAspect.annotated("user_id", "uId")
                  _       <- ZIO.attempt(logger2.debug("hello2 {} debug", "world"))
                  _       <- ZIO.attempt(logger2.warn("hello2 {} warn", "world"))
                  _       <- ZIO.attempt(logger3.info("hello3 {} info", "world"))
                  _       <- ZIO.attempt(logger3.warn("hello3 {} warn", "world"))
                } yield ()).exit
      output <- ZTestLogger.logOutput
      lines   = output.map { logEntry =>
                  val logSpans = if (config.loggerNameLogSpan) {
                    logEntry.spans.map(_.label)
                  } else {
                    List.empty
                  }

                  LogEntry(
                    logSpans,
                    logEntry.logLevel,
                    logEntry.annotations,
                    logEntry.message(),
                    logEntry.cause
                  )
                }
    } yield assertTrue(
      lines == Chunk(
        LogEntry(
          if (config.loggerNameLogSpan) List("test.abc") else List.empty,
          LogLevel.Debug,
          if (config.fiberRefPropagation) Map(zio.logging.loggerNameAnnotationKey -> "test.abc", "user_id" -> "uId")
          else Map(zio.logging.loggerNameAnnotationKey                            -> "test.abc"),
          "test debug message",
          Cause.empty
        ),
        LogEntry(
          if (config.loggerNameLogSpan) List("test.abc") else List.empty,
          LogLevel.Warning,
          if (config.fiberRefPropagation) Map(zio.logging.loggerNameAnnotationKey -> "test.abc", "user_id" -> "uId")
          else Map(zio.logging.loggerNameAnnotationKey                            -> "test.abc"),
          "test warn message",
          Cause.empty
        ),
        LogEntry(
          if (config.loggerNameLogSpan) List("test.logger.def") else List.empty,
          LogLevel.Warning,
          Map(zio.logging.loggerNameAnnotationKey -> "test.logger.def"),
          "hello2 world warn",
          Cause.empty
        ),
        LogEntry(
          if (config.loggerNameLogSpan) List("test.test.logger.xyz") else List.empty,
          LogLevel.Warning,
          Map(zio.logging.loggerNameAnnotationKey -> "test.test.logger.xyz"),
          "hello3 world warn",
          Cause.empty
        )
      )
    )

}
