package zio.logging.example

import zio.logging.backend.SLF4J
import zio.{ ExitCode, Runtime, Scope, URIO, ZIO, ZIOAppDefault }

object Slf4jExampleApp extends ZIOAppDefault {

  private val logger = Runtime.removeDefaultLoggers >>> SLF4J.slf4j()

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
    } yield ExitCode.success).provide(LivePingService.layer ++ logger)

}
