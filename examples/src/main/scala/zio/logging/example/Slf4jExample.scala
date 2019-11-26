package zio.logging.example

import java.util.UUID

import zio.logging.{ logger, AbstractLogging, Logging, Slf4jLogger }

object Slf4jExample extends zio.App {

  val env =
    for {
      logger <- Slf4jLogger()
    } yield new Logging {
      override def logging: AbstractLogging.Service[Any, String] = logger
    }

  override def run(args: List[String]) =
    (for {
      fiber <- logger.addToContext("correlationId", UUID.randomUUID().toString).fork
      _     <- logger.info("info message without correlation id")
      _     <- fiber.join
      _     <- logger.info("info message with correlation id")
      _     <- logger.info("another info message with correlation id").fork
    } yield 1).provideSomeM(env)

}
