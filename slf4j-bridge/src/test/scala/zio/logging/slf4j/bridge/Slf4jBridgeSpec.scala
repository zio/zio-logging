package zio.logging.slf4j.bridge

import org.slf4j.MarkerFactory
import org.slf4j.impl.StaticMarkerBinder
import zio.test._
import zio.{ Cause, Chunk, LogLevel, ZIO, ZIOAspect }

object Slf4jBridgeSpec extends ZIOSpecDefault {

  final case class LogEntry(
                             span: List[String],
                             level: LogLevel,
                             annotations: Map[String, String],
                             message: String,
                             cause: Cause[Any]
                           )

  override def spec =
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
      }.provide(Slf4jBridge.initializeWithoutFiberRefPropagation)
    ) @@ TestAspect.sequential
}
