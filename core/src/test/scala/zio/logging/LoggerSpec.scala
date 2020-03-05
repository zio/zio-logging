package zio.logging

import java.time.OffsetDateTime

import zio._
import zio.test.Assertion._
import zio.test._

object TestLogger {
  type TestLogging = Has[TestLogger.Service]
  trait Service extends Logging.Service {
    def lines: UIO[Vector[(LogContext, String)]]
  }
  def make: ZLayer.NoDeps[Nothing, TestLogging] =
    ZLayer.fromEffect(for {
      data    <- Ref.make(Vector.empty[(LogContext, String)])
      logger0 <- Logger.make((context, message) => data.update(_ :+ ((context, message))).unit)
    } yield new Service {
      override def lines: UIO[Vector[(LogContext, String)]] = data.get

      override def logger: Logger = logger0
    })

  def lines = ZIO.accessM[TestLogging](_.get.lines)
}

object LoggerSpec extends DefaultRunnableSpec {

  def spec =
    suite("logger")(
      testM("simple log") {
        Logging.log("test") *>
          assertM(TestLogger.lines)(
            equalTo(
              Vector(
                (
                  LogContext.empty,
                  "test"
                )
              )
            )
          )
      },
      testM("log with log level") {
        Logging.log(LogLevel.Debug)("test") *>
          assertM(TestLogger.lines)(
            equalTo(
              Vector(
                (
                  LogContext.empty.annotate(LogAnnotation.Level, LogLevel.Debug),
                  "test"
                )
              )
            )
          )
      },
      testM("log annotations") {
        val exampleAnnotation = LogAnnotation[String](
          name = "annotation-name",
          initialValue = "unknown-annotation-value",
          combine = (oldValue, newValue) => oldValue + " " + newValue,
          render = identity
        )

        Logging.logger.flatMap(
          _.locallyAnnotate(exampleAnnotation, "value1")(Logging.log("line1")) *>
            assertM(TestLogger.lines)(
              equalTo(
                Vector(
                  (
                    LogContext.empty
                      .annotate(exampleAnnotation, "value1"),
                    "line1"
                  )
                )
              )
            )
        )
      },
      testM("log annotations apply method") {
        val exampleAnnotation = LogAnnotation[String](
          name = "annotation-name",
          initialValue = "unknown-annotation-value",
          combine = (oldValue, newValue) => oldValue + " " + newValue,
          render = identity
        )

        Logging.locally(exampleAnnotation("value1"))(Logging.log("line1")) *>
          assertM(TestLogger.lines)(
            equalTo(
              Vector(
                (
                  LogContext.empty
                    .annotate(
                      exampleAnnotation,
                      exampleAnnotation.combine(exampleAnnotation.initialValue, "value1")
                    ),
                  "line1"
                )
              )
            )
          )
      },
      testM("named logger") {
        Logging.logger.flatMap(logger =>
          logger.locallyAnnotate(LogAnnotation.Name, List("first"))(logger.named("second").log("line1")) *>
            assertM(TestLogger.lines)(
              equalTo(
                Vector(
                  (
                    LogContext.empty
                      .annotate(LogAnnotation.Name, List("first", "second")),
                    "line1"
                  )
                )
              )
            )
        )
      },
      testM("locallyM") {
        val timely = LogAnnotation[OffsetDateTime](
          "time",
          OffsetDateTime.MIN,
          (_, newVal) => newVal,
          _.toString
        )
        import zio.clock._
        Logging.logger.flatMap(logger =>
          logger.locallyM(ctx => currentDateTime.map(now => ctx.annotate(timely, now)))(logger.log("line1")) *>
            ZIO
              .accessM[Clock](_.get.currentDateTime)
              .flatMap(now =>
                assertM(TestLogger.lines)(
                  equalTo(
                    Vector(
                      (
                        LogContext.empty
                          .annotate(timely, now),
                        "line1"
                      )
                    )
                  )
                )
              )
        )
      }
    ).provideCustomLayer(TestLogger.make)
}
