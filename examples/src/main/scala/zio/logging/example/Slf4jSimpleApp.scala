package zio.logging.example

import zio.logging.LogFormat
import zio.logging.backend.SLF4J
import zio.{ ExitCode, LogLevel, Runtime, Scope, ZIO, ZIOAppDefault }

object Slf4jSimpleApp extends ZIOAppDefault {

  private val slf4jLogger =
    Runtime.removeDefaultLoggers >>> SLF4J.slf4j(LogLevel.Info, LogFormat.line |-| LogFormat.cause)

  override def run: ZIO[Scope, Any, ExitCode] =
    ZIO.logInfo("Hello from ZIO logger").provide(slf4jLogger).as(ExitCode.success)

}
