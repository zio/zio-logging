package zio.logging

import zio._
import zio.console._
import zio.clock._

object Examples extends zio.App {
  val correlationId = LogAnnotation[String](
    name = "correlationId",
    initialValue = "undefined-correlation-id",
    combine = (_, newValue) => newValue,
    render = identity
  )

  val logFormat = "[correlation-id = %s] %s"

  val env =
    //Slf4jLogger.make((context, message) => logFormat.format(context(correlationId), message))
  ZLayer.requires[Console with Clock] ++ LogAppender.console(LogLevel.Info, LogFormat.SimpleConsoleLogFormat((_, s) => s)) >>> Logging.makeWithTimestamp()

  override def run(args: List[String]) =
    (for {
      fiber <- log.locally(correlationId("1234"))(ZIO.unit).fork
      _     <- log.info("info message without correlation id")
      _     <- fiber.join
      _     <- log.locally(correlationId("1234111")) {
                 log.info("info message with correlation id") *>
                   log.throwable("this is error", new RuntimeException("error message")).fork
               }
    } yield ExitCode.success).provideLayer(env)
}
