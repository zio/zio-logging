package zio.logging

import zio.logging.LogFormat.AssembledLogFormat
import zio.logging.LogFormat.AssembledLogFormat.DSL
import zio.logging.LogFormat.AssembledLogFormat.DSL.{ bracketed, timestamp, LEVEL }
import zio.test._
import zio.test.Assertion._
import zio.test.environment.TestEnvironment

import java.time.{ OffsetDateTime, ZoneOffset }
import scala.util.control.NoStackTrace

object LogFormatSpec extends DefaultRunnableSpec {

  def assembledFormat1: LogFormat[String] = AssembledLogFormat {
    import DSL._

    bracketed(LEVEL) >+>
      timestamp(LogDatetimeFormatter.isoLocalDateTimeFormatter) >+>
      name >+>
      line >>>
      error
  }

  object TestException extends Exception("test exception") with NoStackTrace

  override def spec: ZSpec[TestEnvironment, Any] =
    suite("LogFormat")(
      test("AssembledLogFormat example")(
        assert(
          assembledFormat1.format(
            LogContext.empty
              .annotate(LogAnnotation.Name, (List("a", "b")))
              .annotate(LogAnnotation.Timestamp, OffsetDateTime.of(2000, 1, 1, 12, 0, 0, 0, ZoneOffset.UTC))
              .annotate(LogAnnotation.Level, LogLevel.Warn),
            "test message"
          )
        )(equalTo("[WARN] 2000-01-01T12:00:00 a.b test message"))
      ),
      test("AssembledLogFormat example with exception")(
        assert(
          assembledFormat1
            .format(
              LogContext.empty
                .annotate(LogAnnotation.Name, (List("a", "b")))
                .annotate(LogAnnotation.Timestamp, OffsetDateTime.of(2000, 1, 1, 12, 0, 0, 0, ZoneOffset.UTC))
                .annotate(LogAnnotation.Level, LogLevel.Error)
                .annotate(LogAnnotation.Throwable, Some(TestException)),
              "failed!"
            )
            .split(System.lineSeparator())
            .toList
        )(
          contains("[ERROR] 2000-01-01T12:00:00 a.b failed!") &&
            contains("zio.logging.LogFormatSpec$TestException$: test exception")
        )
      )
    )
}
