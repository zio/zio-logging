package zio.logging.slf4j.bridge

import org.slf4j.{Logger, LoggerFactory}
import zio.logging.{LogFilter, LogFormat, LoggerNameExtractor, consoleJson}
import zio.{ExitCode, LogLevel, Runtime, Scope, ZIO, ZIOAppArgs, ZIOAppDefault, ZLayer}

object Slf4jBridgeExampleApp extends ZIOAppDefault {

  private val slf4jLogger: Logger = LoggerFactory.getLogger("SLF4J-LOGGER")

  private val loggerName = LoggerNameExtractor.annotationOrTrace(Slf4jBridge.loggerNameAnnotationKey)

  private val logFilter: LogFilter[String] = LogFilter.logLevelByGroup(
    LogLevel.Info,
    loggerName.toLogGroup(),
    "zio.logging.slf4j" -> LogLevel.Debug,
    "SLF4J-LOGGER" -> LogLevel.Warning
  )

  override val bootstrap: ZLayer[ZIOAppArgs, Any, Any] =
    Runtime.removeDefaultLoggers >>> consoleJson(
      LogFormat.label("name", loggerName.toLogFormat()) + LogFormat.default,
      logFilter
    ) >+> Slf4jBridge.initialize

  override def run: ZIO[Scope, Any, ExitCode] =
    (for {
      _ <- ZIO.logInfo("Start")
      _ <- ZIO.succeed(slf4jLogger.warn("Test {}!", "WARNING"))
      _ <- ZIO.logDebug("Done")
    } yield ExitCode.success)

}
