package zio.logging

import zio._
import zio.logging.slf4j._

object Examples extends zio.App {
  val correlationId = LogAnnotation[String](
    name = "correlationId",
    neutral = "undefined-correlation-id",
    combine = (_, newValue) => newValue,
    render = identity
  )

  val logFormat = "[correlation-id = %s] %s"

  val env =
    Slf4jLogger.make(
      (context, message) =>
        logFormat.format(context.get(correlationId), message)
    )


  override def run(args: List[String]) =
    logger.locallyAnnotate(LogAnnotation.Level, LogLevel.Info) {
      for {
        fiber <- logger.locallyAnnotate(correlationId, "1234")(ZIO.unit).fork
        _ <- logger.log("info message without correlation id")
        _ <- fiber.join
        _ <- logger.locallyAnnotate(correlationId, "1234111") {
          logger.log("info message with correlation id") *>
            logger.log("another info message with correlation id").fork
        }
      } yield 1
    }.provideSomeM(env)
}
