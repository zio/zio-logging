package zio.logging

import zio._
import zio.logging.slf4j.Slf4jLogger

object Examples extends zio.App {
  val correlationId = LogAnnotation[String](
    name = "correlationId",
    initialValue = "undefined-correlation-id",
    combine = (_, newValue) => newValue,
    render = identity
  )

  val logFormat = "[correlation-id = %s] %s"

  val env =
    Slf4jLogger.make((context, message) => logFormat.format(context.get(correlationId), message))

  override def run(args: List[String]) =
    (for {
      fiber <- logLocally(correlationId("1234"))(ZIO.unit).fork
      _     <- log("info message without correlation id")
      _     <- fiber.join
      _ <- logLocally(correlationId("1234111")) {
            log("info message with correlation id") *>
              log(LogLevel.Error)("another info message with correlation id").fork
          }
    } yield 1).provideSomeLayer(env)
}
