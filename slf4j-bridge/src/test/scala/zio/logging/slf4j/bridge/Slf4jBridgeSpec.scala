package zio.logging.slf4j.bridge

import zio.test._
import zio.{ Cause, Chunk, LogLevel, ZIO }

object Slf4jBridgeSpec extends ZIOSpecDefault {

  final case class LogEntry(
    span: List[String],
    level: LogLevel,
    annotations: Map[String, String],
    message: String,
    cause: Cause[Any]
  )

  override def spec: Spec[TestEnvironment, TestSuccess] =
    suite("Slf4jBridge")(
      test("logs through slf4j") {
        val testFailure = new RuntimeException("test error")
        for {
          _      <- (for {
                      logger <- ZIO.attempt(org.slf4j.LoggerFactory.getLogger("test.logger"))
                      _      <- ZIO.attempt(logger.debug("test debug message"))
                      _      <- ZIO.attempt(logger.warn("hello {}", "world"))
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
              List("test.logger"),
              LogLevel.Debug,
              Map.empty,
              "test debug message",
              Cause.empty
            ),
            LogEntry(
              List("test.logger"),
              LogLevel.Warning,
              Map.empty,
              "hello world",
              Cause.empty
            ),
            LogEntry(
              List("test.logger"),
              LogLevel.Warning,
              Map.empty,
              "3..2..1 ... go!",
              Cause.empty
            ),
            LogEntry(
              List("test.logger"),
              LogLevel.Warning,
              Map.empty,
              "warn cause",
              Cause.die(testFailure)
            ),
            LogEntry(
              List("test.logger"),
              LogLevel.Error,
              Map.empty,
              "error",
              Cause.die(testFailure)
            ),
            LogEntry(
              List("test.logger"),
              LogLevel.Error,
              Map.empty,
              "error",
              Cause.empty
            )
          )
        )
      }.provide(Slf4jBridge.initialize)
    )
}
