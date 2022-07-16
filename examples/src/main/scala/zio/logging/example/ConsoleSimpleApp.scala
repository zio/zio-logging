package zio.logging.example

import zio.logging.{ LogFormat, console }
import zio.{ ExitCode, Runtime, Scope, ZIO, ZIOAppDefault }

object ConsoleSimpleApp extends ZIOAppDefault {

  private val logger =
    Runtime.removeDefaultLoggers >>> console(LogFormat.default)

  override def run: ZIO[Scope, Any, ExitCode] =
    ZIO.logInfo("Hello from ZIO logger").provide(logger).as(ExitCode.success)

}
