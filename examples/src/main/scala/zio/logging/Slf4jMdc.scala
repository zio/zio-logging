package zio.logging

import java.util.UUID

import zio.logging.slf4j.Slf4jLogger
import zio.clock.Clock
import zio.{ App, UIO, ZIO }
import zio.duration._

object Slf4jMdc extends App {

  val userId = LogAnnotation[UUID](
    name = "user-id",
    initialValue = UUID.fromString("0-0-0-0-0"),
    combine = (_, newValue) => newValue,
    render = _.toString
  )

  val logLayer = Slf4jLogger.makeWithAnnotationsAsMdc(List(userId))
  val users    = List.fill(2)(UUID.randomUUID())

  override def run(args: List[String]): ZIO[zio.ZEnv, Nothing, Int] =
    (for {
      _             <- log.info("Start...")
      correlationId <- UIO(Some(UUID.randomUUID()))
      _             <- ZIO.foreachPar(users) { uId =>
             log.locally(_.annotate(userId, uId).annotate(LogAnnotation.CorrelationId, correlationId)) {
               log.info("Starting operation") *>
                 ZIO.sleep(500.millis) *>
                 log.info("Stopping operation")
             }
           }
    } yield 0).provideSomeLayer[Clock](logLayer)
}
