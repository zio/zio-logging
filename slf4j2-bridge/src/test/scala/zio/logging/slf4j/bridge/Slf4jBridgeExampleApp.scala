package zio.logging.slf4j.bridge

import zio.logging.{ LogFormat, consoleJson }
import zio.{ ExitCode, LogLevel, Runtime, Scope, ZIO, ZIOAppArgs, ZIOAppDefault, ZLayer }

object Slf4jBridgeExampleApp extends ZIOAppDefault {

  private val slf4jLogger = org.slf4j.LoggerFactory.getLogger("SLF4J-LOGGER")

  override val bootstrap: ZLayer[ZIOAppArgs, Any, Any] =
    Runtime.removeDefaultLoggers >>> consoleJson(LogFormat.default, LogLevel.Debug) >+> Slf4jBridge.initialize

  override def run: ZIO[Scope, Any, ExitCode] =
    (for {
      _ <- ZIO.logInfo("Start")
      _ <- ZIO.succeed(slf4jLogger.warn("Test {}!", "WARNING"))
      _ <- ZIO.logDebug("Done")
    } yield ExitCode.success)

}
