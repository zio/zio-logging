package zio.logging.slf4j.bridge

import zio.logging._
import zio.{ ExitCode, LogLevel, Runtime, Scope, ZIO, ZIOAppArgs, ZIOAppDefault, ZLayer }

import java.util.UUID

object Slf4jBridgeExampleApp extends ZIOAppDefault {

  private val slf4jLogger = org.slf4j.LoggerFactory.getLogger("SLF4J-LOGGER")

  private val logFilter: LogFilter[String] = LogFilter.logLevelByName(
    LogLevel.Info,
    "zio.logging.slf4j" -> LogLevel.Debug,
    "SLF4J-LOGGER"      -> LogLevel.Warning
  )

  override val bootstrap: ZLayer[ZIOAppArgs, Any, Any] =
    Runtime.enableCurrentFiber ++ Runtime.removeDefaultLoggers >>> consoleJsonLogger(
      ConsoleLoggerConfig(
        LogFormat.label(
          "name",
          LoggerNameExtractor.loggerNameAnnotationOrTrace.toLogFormat()
        ) + LogFormat.allAnnotations + LogFormat.default,
        logFilter
      )
    ) >+> Slf4jBridge.initialize

  private val uuids = List.fill(2)(UUID.randomUUID())

  override def run: ZIO[Scope, Any, ExitCode] =
    for {
      _ <- ZIO.logInfo("Start")
      _ <- ZIO.foreachPar(uuids) { u =>
             ZIO.succeed(slf4jLogger.warn("Test {}! xxx", "WARNING")) @@ LogAnnotation.UserId(
               u.toString
             )
           } @@ LogAnnotation.TraceId(UUID.randomUUID())
      _ <- ZIO.logDebug("Done")
    } yield ExitCode.success

}
