package zio.logging.slf4j.bridge

import zio.ZIO
import zio.logging.LoggerSpec.TestLogger
import zio.logging.{ LogAnnotation, LogContext, LogLevel }
import zio.test.Assertion._
import zio.test.TestAspect.sequential
import zio.test._
import zio.test.environment.TestEnvironment

object Slf4jBridgeSpec extends DefaultRunnableSpec {
  override def spec: Spec[TestEnvironment, TestFailure[Throwable], TestSuccess] =
    suite("Slf4jBridge")(
      test("logs through slf4j") {
        for {
          logger     <- ZIO.attempt(org.slf4j.LoggerFactory.getLogger("test.logger"))
          _          <- ZIO.attempt(logger.debug("test debug message"))
          _          <- ZIO.attempt(logger.warn("hello %s", "world"))
          testFailure = new RuntimeException("test error")
          _          <- ZIO.attempt(logger.warn("warn cause", testFailure))
          _          <- ZIO.attempt(logger.error("error", testFailure))
          lines      <- TestLogger.lines
        } yield assert(lines)(
          equalTo(
            /*
             Vector(
             (LogContext(Map(LogAnnotation(name) -> List(test-logger), LogAnnotation(level) -> Debug)),test debug message),
             (LogContext(Map(LogAnnotation(name) -> List(test-logger), LogAnnotation(level) -> Warn)),hello world),
             (LogContext(Map(LogAnnotation(name) -> List(test-logger), LogAnnotation(throwable) -> Some(java.lang.RuntimeException: test error), LogAnnotation(level) -> Error)),error))
             */
            Vector(
              (
                LogContext.empty
                  .annotate(LogAnnotation.Level, LogLevel.Debug)
                  .annotate(LogAnnotation.Name, List("test", "logger")),
                "test debug message"
              ),
              (
                LogContext.empty
                  .annotate(LogAnnotation.Level, LogLevel.Warn)
                  .annotate(LogAnnotation.Name, List("test", "logger")),
                "hello world"
              ),
              (
                LogContext.empty
                  .annotate(LogAnnotation.Level, LogLevel.Warn)
                  .annotate(LogAnnotation.Name, List("test", "logger"))
                  .annotate(LogAnnotation.Throwable, Some(testFailure)),
                "warn cause"
              ),
              (
                LogContext.empty
                  .annotate(LogAnnotation.Level, LogLevel.Error)
                  .annotate(LogAnnotation.Name, List("test", "logger"))
                  .annotate(LogAnnotation.Throwable, Some(testFailure)),
                "error"
              )
            )
          )
        )
      },
      test("Initialize MDC") {
        for {
          _ <- ZIO.attempt(org.slf4j.MDC.clear())
        } yield assert(true)(equalTo(true))
      }
    ) @@ sequential provideCustomLayer (TestLogger.make >>> initializeSlf4jBridge)
}
