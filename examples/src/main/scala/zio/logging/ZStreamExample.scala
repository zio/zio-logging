package zio.logging

import zio._
import zio.stream.ZStream

import java.util.UUID
import zio.clock.Clock

object ZStreamExample extends zio.App {

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

  final val env: ZLayer[zio.console.Console with Clock, Nothing, Logging] =
    Logging.console(
      logLevel = LogLevel.Debug,
      format = LogFormat.ColoredLogFormat((ctx, line) => s"${ctx(CalculationId)} ${ctx(CalculationNumber)} $line")
    ) >>>
      Logging.withRootLoggerName("my-logger")

  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] = {

    val stream = for {
      calcNumber <- ZStream(1, 2, 3, 4, 5)

      subStream <-
        log.locallyZStream(CalculationId(Some(UUID.randomUUID())).andThen(CalculationNumber(calcNumber)))(
          ZStream.fromEffect(log.debug(s"would log first line for calculation# ${calcNumber}")) *>
            ZStream.fromEffect(log.debug(s"would log second line for calculation# ${calcNumber}"))
        )

    } yield subStream

    stream.runDrain.provideSomeLayer(env).exitCode
  }
}
