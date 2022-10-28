package zio.logging.example

import zio.logging.backend.JPL
import zio.{ ExitCode, Runtime, Scope, URIO, ZIO, ZIOAppArgs, ZIOAppDefault, ZLayer }

object JplExampleApp extends ZIOAppDefault {

  override val bootstrap: ZLayer[ZIOAppArgs with Scope, Any, Any] = Runtime.removeDefaultLoggers >>> JPL.jpl

  private def ping(address: String): URIO[PingService, Unit] =
    PingService
      .ping(address)
      .tap(result => ZIO.logInfo(s"ping: $address - result: $result"))
      .tapErrorCause(error => ZIO.logErrorCause(s"ping: $address - error", error))
      .unit
      .ignore

  override def run: ZIO[Scope, Any, ExitCode] =
    (for {
      _ <- ping("127.0.0.1")
      _ <- ping("x8.8.8.8")
    } yield ExitCode.success).provide(LivePingService.layer)

}
