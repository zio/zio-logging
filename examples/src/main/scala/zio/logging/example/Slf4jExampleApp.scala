package zio.logging.example

import zio.logging.LogFormat
import zio.logging.backend.SLF4J
import zio.{ Cause, ExitCode, Runtime, Scope, URIO, ZIO, ZIOAppDefault }

object Slf4jExampleApp extends ZIOAppDefault {

  private val slf4jLogger =
    Runtime.removeDefaultLoggers >>> SLF4J.slf4j(zio.LogLevel.Debug, LogFormat.line |-| LogFormat.cause)

  private def ping(address: String): URIO[PingService, Unit] =
    PingService
      .ping(address)
      .foldZIO(
        e => ZIO.logErrorCause(s"ping: $address - error", Cause.fail(e)),
        r => ZIO.logInfo(s"ping: $address - result: $r")
      )

  override def run: ZIO[Scope, Any, ExitCode] =
    (for {
      _ <- ping("127.0.0.1")
      _ <- ping("x8.8.8.8")
    } yield ExitCode.success).provide(LivePingService.layer ++ slf4jLogger)

}
