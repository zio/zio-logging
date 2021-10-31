package zio.logging

import zio.stream.ZStream
import zio.{ Clock, Console, ExitCode, Has, URIO, ZIOAppDefault, ZLayer }

import java.util.UUID

object ZStreamExample extends ZIOAppDefault {

  final val CalculationId: LogAnnotation[Option[UUID]] = LogAnnotation[Option[UUID]](
    name = "calculation-id",
    initialValue = None,
    combine = (_, r) => r,
    render = _.map(_.toString).getOrElse("undefined-calculation-id")
  )

  final val CalculationNumber: LogAnnotation[Int] = LogAnnotation[Int](
    name = "calculation-number",
    initialValue = 0,
    combine = (_, r) => r,
    render = _.toString
  )

  final val env: ZLayer[Has[Console] with Has[Clock], Nothing, Logging] =
    Logging.console(
      logLevel = LogLevel.Debug,
      format = LogFormat.ColoredLogFormat((ctx, line) => s"${ctx(CalculationId)} ${ctx(CalculationNumber)} $line")
    ) >>>
      Logging.withRootLoggerName("my-logger")

  override def run: URIO[zio.ZEnv, ExitCode] = {

    val stream = for {
      calcNumber <- ZStream(1, 2, 3, 4, 5)

      subStream <-
        log.locallyZStream(CalculationId(Some(UUID.randomUUID())).andThen(CalculationNumber(calcNumber)))(
          ZStream.fromZIO(log.debug(s"would log first line for calculation# ${calcNumber}")) *>
            ZStream.fromZIO(log.debug(s"would log second line for calculation# ${calcNumber}"))
        )

    } yield subStream

    stream.runDrain.provideSomeLayer(env).exitCode
  }
}
