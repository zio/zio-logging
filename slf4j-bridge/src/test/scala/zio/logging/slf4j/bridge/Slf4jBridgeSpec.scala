package zio.logging.slf4j.bridge

import org.slf4j.MarkerFactory
import org.slf4j.impl.StaticMarkerBinder
import zio.logging.LogFilter
import zio.test.{ Spec, _ }
import zio.{ Cause, Chunk, ConfigProvider, FiberRef, LogLevel, Runtime, ZIO, ZIOAspect }

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
      test("does not run FiberRef.join on log calls") {
        val joins = new java.util.concurrent.atomic.AtomicInteger(0)
        ZIO.scoped[Any] {
          for {
            ref    <- FiberRef.make[Option[String]](
                        initial = None,
                        fork = _ => None,
                        join = (parent, _) => { joins.incrementAndGet(); parent }
                      )
            logger <- ZIO.attempt(org.slf4j.LoggerFactory.getLogger("test.join"))
            _      <- ref.locally(Some("resource")) {
                        for {
                          _ <- ZIO.attempt(logger.info("logging inside resource scope 1"))
                          _ <- ZIO.attempt(logger.info("logging inside resource scope 2"))
                          _ <- ZIO.attempt(logger.info("logging inside resource scope 3"))
                        } yield ()
                      }
            count  <- ZIO.succeed(joins.get())
          } yield assertTrue(count == 0)
        }
      }.provide(Slf4jBridge.initialize),
      test("parallel fibers propagate distinct annotations and spans") {
        for {
          logger <- ZIO.attempt(org.slf4j.LoggerFactory.getLogger("test.concurrent"))
          _      <- ZIO.foreachParDiscard(1 to 50) { i =>
                      ZIO.logAnnotate("fiber_id", i.toString) {
                        ZIO.logSpan(s"span_$i") {
                          ZIO.attempt(logger.info(s"message from fiber $i"))
                        }
                      }
                    }
          output <- ZTestLogger.logOutput
          msgs    = output.filter(_.annotations.get("logger_name").contains("test.concurrent"))
        } yield assertTrue(
          msgs.length == 50,
          msgs.forall { entry =>
            val id = entry.annotations("fiber_id")
            entry.message() == s"message from fiber $id" &&
            entry.spans.exists(_.label == s"span_$id")
          }
        )
      }.provide(Slf4jBridge.initialize),
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
        filterTest
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
        Runtime.setConfigProvider(configProvider) >>> Slf4jBridge.init()
      }
    ) @@ TestAspect.sequential

  def filterTest: ZIO[Any, Nothing, TestResult] =
    for {
      _      <- (for {
                  logger1 <- ZIO.attempt(org.slf4j.LoggerFactory.getLogger("test.abc"))
                  logger2 <- ZIO.attempt(org.slf4j.LoggerFactory.getLogger("test.logger.def"))
                  logger3 <- ZIO.attempt(org.slf4j.LoggerFactory.getLogger("test.test.logger.xyz"))
                  _       <- ZIO.attempt(logger1.debug("test debug message"))
                  _       <- ZIO.attempt(logger1.warn("test warn message"))
                  _       <- ZIO.attempt(logger2.debug("hello2 {} debug", "world"))
                  _       <- ZIO.attempt(logger2.warn("hello2 {} warn", "world"))
                  _       <- ZIO.attempt(logger3.info("hello3 {} info", "world"))
                  _       <- ZIO.attempt(logger3.warn("hello3 {} warn", "world"))
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
          "test warn message",
          Cause.empty
        ),
        LogEntry(
          List("test.logger.def"),
          LogLevel.Warning,
          Map(zio.logging.loggerNameAnnotationKey -> "test.logger.def"),
          "hello2 world warn",
          Cause.empty
        ),
        LogEntry(
          List("test.test.logger.xyz"),
          LogLevel.Warning,
          Map(zio.logging.loggerNameAnnotationKey -> "test.test.logger.xyz"),
          "hello3 world warn",
          Cause.empty
        )
      )
    )

}
