package zio.logging

import java.time.OffsetDateTime

import zio.test.Assertion._
import zio.test._
import zio.test.environment.TestClock
import zio.{ Ref, UIO, ZIO }

object LoggerSpec
    extends DefaultRunnableSpec({

      case class TestLogger(ref: Ref[Vector[(LogContext, String)]], logger: Logger) extends Logger {
        override def locally[R, E, A1](f: LogContext => LogContext)(zio: ZIO[R, E, A1]): ZIO[R, E, A1] =
          logger.locally(f)(zio)
        override def log(line: => String): UIO[Unit] =
          logger.log(line)
        override def logContext: UIO[LogContext] =
          logger.logContext

        def lines: UIO[Vector[(LogContext, String)]] = ref.get
      }
      object TestLogger {
        def apply: UIO[TestLogger] =
          for {
            data   <- Ref.make(Vector.empty[(LogContext, String)])
            logger <- Logger.make((context, message) => data.update(_ :+ ((context, message))).unit)
          } yield new TestLogger(data, logger)

      }

      suite("logger")(
        testM("simple log") {
          TestLogger.apply.flatMap(logger =>
            logger.log("test") *>
              assertM(
                logger.lines,
                equalTo(
                  Vector(
                    (
                      LogContext.empty,
                      "test"
                    )
                  )
                )
              )
          )
        },
        testM("log with log level") {
          TestLogger.apply.flatMap(logger =>
            logger.log(LogLevel.Debug)("test") *>
              assertM(
                logger.lines,
                equalTo(
                  Vector(
                    (
                      LogContext.empty.annotate(LogAnnotation.Level, LogLevel.Debug),
                      "test"
                    )
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

          TestLogger.apply.flatMap(logger =>
            logger.locallyAnnotate(exampleAnnotation, "value1")(logger.log("line1")) *>
              assertM(
                logger.lines,
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

          TestLogger.apply.flatMap(logger =>
            logger.locally(exampleAnnotation("value1"))(logger.log("line1")) *>
              assertM(
                logger.lines,
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
          )
        },
        testM("named logger") {
          TestLogger.apply.flatMap(logger =>
            logger.locallyAnnotate(LogAnnotation.Name, List("first"))(logger.named("second").log("line1")) *>
              assertM(
                logger.lines,
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
          TestLogger.apply.flatMap(logger =>
            logger.locallyM(ctx => currentDateTime.map(now => ctx.annotate(timely, now)))(logger.log("line1")) *>
              ZIO
                .accessM[TestClock](_.clock.currentDateTime)
                .flatMap(now =>
                  assertM(
                    logger.lines,
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
      )
    })
