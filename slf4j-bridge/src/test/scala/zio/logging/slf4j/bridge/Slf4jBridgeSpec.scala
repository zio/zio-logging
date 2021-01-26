package zio.logging.slf4j.bridge

import zio.ZIO
import zio.logging.LoggerSpec.TestLogger
import zio.logging.{ LogAnnotation, LogContext, LogLevel }
import zio.test.Assertion._
import zio.test._

object Slf4jBridgeSpec extends DefaultRunnableSpec {
  override def spec =
    suite("Slf4jBridge")(
      testM("logs through slf4j") {
        for {
          logger     <- ZIO.effect(org.slf4j.LoggerFactory.getLogger("test.logger"))
          _          <- ZIO.effect(logger.debug("test debug message"))
          _          <- ZIO.effect(logger.warn("hello %s", "world"))
          testFailure = new RuntimeException("test error")
          _          <- ZIO.effect(logger.error("error", testFailure))
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
                  .annotate(LogAnnotation.Level, LogLevel.Error)
                  .annotate(LogAnnotation.Name, List("test", "logger"))
                  .annotate(LogAnnotation.Throwable, Some(testFailure)),
                "error"
              )
            )
          )
        )
      }
    ).provideCustomLayer(TestLogger.make >>> initializeSlf4jBridge)
}
