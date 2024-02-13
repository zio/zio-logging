package zio.logging.slf4j.bridge

import zio.logging._
import zio.{ ExitCode, LogLevel, Runtime, Scope, ZIO, ZIOAppArgs, ZIOAppDefault, ZLayer }

import java.util.UUID

object Slf4jBridgeExampleApp extends ZIOAppDefault {

  private val slf4jLogger = org.slf4j.LoggerFactory.getLogger("SLF4J-LOGGER")

  private val logFilterConfig = LogFilter.LogLevelByNameConfig(
    LogLevel.Info,
    "zio.logging.slf4j" -> LogLevel.Debug,
    "SLF4J-LOGGER"      -> LogLevel.Warning
  )

  private val logFormat = LogFormat.label(
    "name",
    LoggerNameExtractor.loggerNameAnnotationOrTrace.toLogFormat()
  ) + LogFormat.logAnnotation(LogAnnotation.UserId) + LogFormat.logAnnotation(
    LogAnnotation.TraceId
  ) + LogFormat.default

  private val loggerConfig = ConsoleLoggerConfig(logFormat, logFilterConfig)

  override val bootstrap: ZLayer[ZIOAppArgs, Any, Any] =
    Runtime.removeDefaultLoggers >>> consoleJsonLogger(loggerConfig) >+> Slf4jBridge.initialize(loggerConfig.toFilter)

  private val uuids = List.fill(2)(UUID.randomUUID())

  override def run: ZIO[Scope, Any, ExitCode] =
    for {
      _ <- ZIO.logInfo("Start")
      _ <- ZIO.foreachPar(uuids) { u =>
             ZIO.succeed(slf4jLogger.info("Test {}!", "INFO")) *> ZIO.succeed(
               slf4jLogger.warn("Test {}!", "WARNING")
             ) @@ LogAnnotation.UserId(
               u.toString
             )
           } @@ LogAnnotation.TraceId(UUID.randomUUID())
      _ <- ZIO.logDebug("Done")
    } yield ExitCode.success

}
