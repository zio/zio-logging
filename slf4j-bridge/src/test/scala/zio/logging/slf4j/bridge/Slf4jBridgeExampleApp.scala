package zio.logging.slf4j.bridge

import zio.logging.{ LogFilter, LogFormat, LoggerNameExtractor, consoleJson }
import zio.{ ExitCode, LogLevel, Runtime, Scope, ZIO, ZIOAppArgs, ZIOAppDefault, ZLayer }

object Slf4jBridgeExampleApp extends ZIOAppDefault {

  private val slf4jLogger = org.slf4j.LoggerFactory.getLogger("SLF4J-LOGGER")

  private val logFilter: LogFilter[String] = LogFilter.logLevelByName(
    LogLevel.Info,
    "zio.logging.slf4j" -> LogLevel.Debug,
    "SLF4J-LOGGER"      -> LogLevel.Warning
  )

  override val bootstrap: ZLayer[ZIOAppArgs, Any, Any] =
    Runtime.removeDefaultLoggers >>> consoleJson(
      LogFormat.label("name", LoggerNameExtractor.loggerNameAnnotationOrTrace.toLogFormat()) + LogFormat.default,
      logFilter
    ) >+> Slf4jBridge.initialize

  override def run: ZIO[Scope, Any, ExitCode] =
    for {
      _ <- ZIO.logDebug("Start")
      _ <- ZIO.succeed(slf4jLogger.debug("Test {}!", "DEBUG"))
      _ <- ZIO.succeed(slf4jLogger.warn("Test {}!", "WARNING"))
      _ <- ZIO.logInfo("Done")
    } yield ExitCode.success

}
