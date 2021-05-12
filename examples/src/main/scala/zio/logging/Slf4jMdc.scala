package zio.logging

import zio._
import zio.clock.Clock
import zio.duration._
import zio.logging.slf4j.Slf4jLogger

import java.util.UUID

object Slf4jMdc extends App {

  val userId: LogAnnotation[UUID] = LogAnnotation[UUID](
    name = "user-id",
    initialValue = UUID.fromString("0-0-0-0-0"),
    combine = (_, newValue) => newValue,
    render = _.toString
  )

  val logLayer: ULayer[Logging] = Slf4jLogger.makeWithAnnotationsAsMdc(List(userId))
  val users: List[UUID]         = List.fill(2)(UUID.randomUUID())

  override def run(args: List[String]): ZIO[zio.ZEnv, Nothing, ExitCode] =
    (for {
      _             <- log.info("Start...")
      correlationId <- UIO.some(UUID.randomUUID())
      _             <- ZIO.foreachPar(users) { uId =>
                         log.locally(_.annotate(userId, uId).annotate(LogAnnotation.CorrelationId, correlationId)) {
                           log.info("Starting operation") *>
                             ZIO.sleep(500.millis) *>
                             log.info("Stopping operation")
                         }
                       }
    } yield ExitCode.success).provideSomeLayer[Clock](logLayer)
}
