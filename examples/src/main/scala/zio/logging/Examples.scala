package zio.logging

import zio._
import zio.logging.slf4j.Slf4jLogger

object Examples extends ZIOAppDefault {

  val correlationId: LogAnnotation[String] = LogAnnotation[String](
    name = "correlationId",
    initialValue = "undefined-correlation-id",
    combine = (_, newValue) => newValue,
    render = identity
  )

  val logFormat = "[correlation-id = %s] %s"

  val env: ULayer[Logging] =
    Slf4jLogger.make((context, message) => logFormat.format(context(correlationId), message))

  override def run: URIO[ZEnv, ExitCode] =
    (for {
      fiber <- log.locally(correlationId("1234"))(ZIO.unit).fork
      _     <- log.info("info message with correlation id from modifyLogger")
      _     <- fiber.join
      _     <- log.locally(correlationId("1234111").andThen(LogAnnotation.Name("other-logger" :: Nil))) {
                 log.info("info message with correlation id") *>
                   log.info("info message with correlation id").delay(5.seconds)
               }
    } yield ExitCode.success).provideCustomLayer(env)
}
