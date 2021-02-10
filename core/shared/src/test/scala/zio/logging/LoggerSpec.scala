package zio.logging

import java.time.OffsetDateTime
import java.util.UUID

import zio.{ FiberRef, Has, Layer, Ref, UIO, ZIO, ZLayer }
import zio.test.Assertion._
import zio.test._

object LoggerSpec extends DefaultRunnableSpec {

  object TestLogger {
    type TestLogging = Has[TestLogger.Service]
    trait Service extends Logger[String] {
      def lines: UIO[Vector[(LogContext, String)]]
    }
    def make: Layer[Nothing, TestLogging with Logging] =
      ZLayer.fromEffectMany(for {
        data   <- Ref.make(Vector.empty[(LogContext, String)])
        logger <- FiberRef
                    .make(LogContext.empty)
                    .map { ref =>
                      new Logger[String] with TestLogger.Service {
                        def locally[R1, E, A](f: LogContext => LogContext)(zio: ZIO[R1, E, A]): ZIO[R1, E, A] =
                          ref.get.flatMap(context => ref.locally(f(context))(zio))

                        def log(line: => String): UIO[Unit] =
                          ref.get.flatMap(context => data.update(_ :+ ((context, line))).unit)

                        def logContext: UIO[LogContext] = ref.get

                        def lines: UIO[Vector[(LogContext, String)]] = data.get
                      }
                    }

      } yield Has.allOf[Logger[String], TestLogger.Service](logger, logger))

    def lines: ZIO[TestLogging, Nothing, Vector[(LogContext, String)]] = ZIO.accessM[TestLogging](_.get.lines)
  }

  def spec =
    suite("logger")(
      testM("log with log level") {
        log.debug("test") *>
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
      testM("log annotations apply method") {
        val exampleAnnotation = LogAnnotation[String](
          name = "annotation-name",
          initialValue = "unknown-annotation-value",
          combine = (oldValue, newValue) => oldValue + " " + newValue,
          render = identity
        )

        log.locally(exampleAnnotation("value1"))(log.info("line1")) *>
          assertM(TestLogger.lines)(
            equalTo(
              Vector(
                (
                  LogContext.empty
                    .annotate(
                      exampleAnnotation,
                      exampleAnnotation.combine(exampleAnnotation.initialValue, "value1")
                    )
                    .annotate(LogAnnotation.Level, LogLevel.Info),
                  "line1"
                )
              )
            )
          )
      },
      testM("named logger") {
        ZIO
          .access[Logging](_.get.named("first"))
          .flatMap(logger => logger.locally(LogAnnotation.Name(List("second")))(logger.log("line1"))) *>
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
      },
      testM("derive") {
        val counter = LogAnnotation[Int](
          name = "counter",
          initialValue = 0,
          combine = _ + _,
          render = _.toString()
        )

        for {
          derived <- log.derive(counter(10))
          _       <- derived.locally(counter(20))(derived.info("fake log"))
          lines   <- TestLogger.lines
        } yield assert(lines)(
          equalTo(
            Vector((LogContext.empty.annotate(LogAnnotation.Level, LogLevel.Info).annotate(counter, 30), "fake log"))
          )
        )
      },
      testM("locallyM") {
        val timely = LogAnnotation[OffsetDateTime](
          name = "time",
          initialValue = OffsetDateTime.MIN,
          combine = (_, newVal) => newVal,
          render = _.toString
        )
        import zio.clock._
        log.locallyM(ctx => currentDateTime.orDie.map(now => ctx.annotate(timely, now)))(log.info("line1")) *>
          ZIO
            .accessM[Clock](_.get.currentDateTime)
            .flatMap(now =>
              assertM(TestLogger.lines)(
                equalTo(
                  Vector(
                    (
                      LogContext.empty
                        .annotate(timely, now)
                        .annotate(LogAnnotation.Level, LogLevel.Info),
                      "line1"
                    )
                  )
                )
              )
            )
      },
      test("LogContext render") {
        val correlationId = UUID.randomUUID()
        val rendered      = LogContext.empty
          .annotate(LogAnnotation.Name, List("logger_name", "second_level"))
          .annotate(LogAnnotation.CorrelationId, Some(correlationId))
          .renderContext

        assert(rendered)(
          equalTo(
            Map(
              LogAnnotation.Name.name          -> "logger_name.second_level",
              LogAnnotation.CorrelationId.name -> correlationId.toString
            )
          )
        )

      }
    ).provideCustomLayer(TestLogger.make)
}
