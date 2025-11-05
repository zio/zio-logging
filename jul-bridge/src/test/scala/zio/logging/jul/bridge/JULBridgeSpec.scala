package zio.logging.jul.bridge

import zio.logging.LogFilter
import zio.test.{ Spec, _ }
import zio.{ Cause, Chunk, ConfigProvider, LogLevel, Runtime, ZIO, ZIOAspect }

import java.util.logging.Level._
import java.util.logging.Logger

object JULBridgeSpec extends ZIOSpecDefault {

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
                .succeed(Logger.getLogger("SLF4J-LOGGER").warning("Test WARNING!"))
                .provide(JULBridge.initialize)
            }
        } yield assertCompletes
      },
      test("logs through slf4j") {
        val testFailure = new RuntimeException("test error")
        for {
          _      <-
            (for {
              logger <- ZIO.attempt(Logger.getLogger("test.logger"))
              _      <- ZIO.logSpan("span")(ZIO.attempt(logger.fine("test fine message"))) @@ ZIOAspect
                          .annotated("trace_id", "tId")
              _      <- ZIO.attempt(logger.warning("hello world")) @@ ZIOAspect.annotated("user_id", "uId")
              _      <- ZIO.attempt(logger.warning("3..2..1 ... go!"))
              _      <- ZIO.attempt(logger.log(WARNING, "warn cause", testFailure))
              _      <- ZIO.attempt(logger.log(SEVERE, "severe", testFailure))
              _      <- ZIO.attempt(logger.log(SEVERE, "severe"))
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
              "test fine message",
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
              LogLevel.Fatal,
              Map(zio.logging.loggerNameAnnotationKey -> "test.logger"),
              "severe",
              Cause.die(testFailure)
            ),
            LogEntry(
              List("test.logger"),
              LogLevel.Fatal,
              Map(zio.logging.loggerNameAnnotationKey -> "test.logger"),
              "severe",
              Cause.empty
            )
          )
        )
      }.provide(JULBridge.initialize),
      test("Implements Logger#getName") {
        for {
          logger <- ZIO.attempt(Logger.getLogger("zio.test.logger"))
        } yield assertTrue(logger.getName == "zio.test.logger")
      }.provide(JULBridge.initialize),
      test("logs through slf4j without fiber ref propagation") {
        for {
          _      <- (for {
                      logger <- ZIO.attempt(Logger.getLogger("test.logger"))
                      _      <- ZIO.logSpan("span")(ZIO.attempt(logger.fine("test fine message"))) @@ ZIOAspect
                                  .annotated("trace_id", "tId")
                      _      <- ZIO.attempt(logger.warning("hello world")) @@ ZIOAspect.annotated("user_id", "uId")
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
              "test fine message",
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
      }.provide(JULBridge.initializeWithoutFiberRefPropagation),
      test("logs through slf4j with filter") {
        filterTest
      }.provide(
        JULBridge.init(
          LogFilter.logLevelByName(
            LogLevel.Debug,
            "test.logger"      -> LogLevel.Info,
            "test.test.logger" -> LogLevel.Warning
          )
        )
      ),
      test("logs through slf4j with filter from config") {
        filterTest
      }.provide {
        val configProvider: ConfigProvider = ConfigProvider.fromMap(
          Map(
            "logger/filter/rootLevel"                 -> "DEBUG",
            "logger/filter/mappings/test.logger"      -> "INFO",
            "logger/filter/mappings/test.test.logger" -> "WARN"
          ),
          "/"
        )
        Runtime.setConfigProvider(configProvider) >>> JULBridge.init()
      }
    ) @@ TestAspect.after(removeExistingHandlers) @@ TestAspect.sequential

  def filterTest: ZIO[Any, Nothing, TestResult] =
    for {
      _      <- (for {
                  logger1 <- ZIO.attempt(Logger.getLogger("test.abc"))
                  logger2 <- ZIO.attempt(Logger.getLogger("test.logger.def"))
                  logger3 <- ZIO.attempt(Logger.getLogger("test.test.logger.xyz"))
                  _       <- ZIO.attempt(logger1.fine("test debug message"))
                  _       <- ZIO.attempt(logger1.warning("test warning message"))
                  _       <- ZIO.attempt(logger2.fine("hello2 world fine"))
                  _       <- ZIO.attempt(logger2.warning("hello2 world warning"))
                  _       <- ZIO.attempt(logger3.info("hello3 world info"))
                  _       <- ZIO.attempt(logger3.warning("hello3 world warning"))
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
          List("test.abc"),
          LogLevel.Debug,
          Map(zio.logging.loggerNameAnnotationKey -> "test.abc"),
          "test debug message",
          Cause.empty
        ),
        LogEntry(
          List("test.abc"),
          LogLevel.Warning,
          Map(zio.logging.loggerNameAnnotationKey -> "test.abc"),
          "test warning message",
          Cause.empty
        ),
        LogEntry(
          List("test.logger.def"),
          LogLevel.Warning,
          Map(zio.logging.loggerNameAnnotationKey -> "test.logger.def"),
          "hello2 world warning",
          Cause.empty
        ),
        LogEntry(
          List("test.test.logger.xyz"),
          LogLevel.Warning,
          Map(zio.logging.loggerNameAnnotationKey -> "test.test.logger.xyz"),
          "hello3 world warning",
          Cause.empty
        )
      )
    )

  def removeExistingHandlers: ZIO[Any, Nothing, Unit] =
    ZIO.succeed(
      Logger.getLogger("").getHandlers.foreach(Logger.getLogger("").removeHandler(_))
    )

}
