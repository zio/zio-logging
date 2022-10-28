package zio.logging.example

import zio.logging.{ LogFormat, console }
import zio.{ ExitCode, Runtime, Scope, ZIO, ZIOAppArgs, ZIOAppDefault, ZLayer }

object ConsoleSimpleApp extends ZIOAppDefault {

  override val bootstrap: ZLayer[ZIOAppArgs with Scope, Any, Any] =
    Runtime.removeDefaultLoggers >>> console(LogFormat.default)

  override def run: ZIO[Scope, Any, ExitCode] =
    ZIO.logInfo("Hello from ZIO logger").as(ExitCode.success)

}
