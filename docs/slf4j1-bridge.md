---
id: slf4j1-bridge
title: "SLF4J v1 bridge"
---

It is possible to use `zio-logging` for SLF4J loggers, usually third-party non-ZIO libraries. To do so, import the `zio-logging-slf4j-bridge` module for SLF4J v1 (working with JDK8):

```scala
libraryDependencies += "dev.zio" %% "zio-logging-slf4j-bridge" % "@VERSION@"
```

and use the `Slf4jBridge.initialize` layer when setting up logging:

```scala
import zio.logging.slf4j.Slf4jBridge

program.provideCustom(Slf4jBridge.initialize)
```

<br/>

SLF4J logger name is stored in log annotation with key `logger_name` (`zio.logging.loggerNameAnnotationKey`), following log format

```scala
import zio.logging.slf4j.Slf4jBridge
import zio.logging.LoggerNameExtractor

val loggerName = LoggerNameExtractor.loggerNameAnnotationOrTrace
val loggerNameFormat = loggerName.toLogFormat()
```
may be used to get logger name from log annotation or ZIO Trace.

<br/>

SLF4J bridge with custom logger can be setup:

```scala
import zio.logging.slf4j.Slf4jBridge
import zio.logging.consoleJsonLogger

val logger = Runtime.removeDefaultLoggers >>> consoleJsonLogger() >+> Slf4jBridge.initialize
```
<br/>

**NOTE:** You should either use `zio-logging-slf4j` to send all ZIO logs to an SLF4j provider (such as logback, log4j etc) OR `zio-logging-slf4j-bridge` to send all SLF4j logs to
ZIO logging. Enabling both causes circular logging and makes no sense.


## Examples

### SLF4J bridge with JSON console logger

[//]: # (TODO: make snippet type-checked using mdoc)

```scala
package zio.logging.example

import zio.logging.slf4j.bridge.Slf4jBridge
import zio.logging.{ ConsoleLoggerConfig, LogAnnotation, LogFilter, LogFormat, LoggerNameExtractor, consoleJsonLogger }
import zio.{ ExitCode, LogLevel, Runtime, Scope, ZIO, ZIOAppArgs, ZIOAppDefault, ZLayer }

import java.util.UUID

object Slf4jBridgeExampleApp extends ZIOAppDefault {

  private val slf4jLogger = org.slf4j.LoggerFactory.getLogger("SLF4J-LOGGER")

  private val logFormat = LogFormat.label(
    "name",
    LoggerNameExtractor.loggerNameAnnotationOrTrace.toLogFormat()
  ) + LogFormat.logAnnotation(LogAnnotation.UserId) + LogFormat.logAnnotation(
    LogAnnotation.TraceId
  ) + LogFormat.default

  override val bootstrap: ZLayer[ZIOAppArgs, Any, Any] =
    Runtime.removeDefaultLoggers >>> consoleJsonLogger(
      ConsoleLoggerConfig(logFormat, logFilterConfig)
    ) >+> Slf4jBridge.initialize

  private val uuids = List.fill(2)(UUID.randomUUID())

  override def run: ZIO[Scope, Any, ExitCode] =
    for {
      _ <- ZIO.logInfo("Start")
      _ <- ZIO.foreachPar(uuids) { u =>
        ZIO.succeed(slf4jLogger.warn("Test {}!", "WARNING")) @@ LogAnnotation.UserId(
          u.toString
        )
      } @@ LogAnnotation.TraceId(UUID.randomUUID())
      _ <- ZIO.logDebug("Done")
    } yield ExitCode.success

}
```

Expected Console Output:
```
{"name":"zio.logging.slf4j.bridge.Slf4jBridgeExampleApp","timestamp":"2023-05-15T20:14:20.712983+02:00","level":"INFO","thread":"zio-fiber-6","message":"Start"}
{"name":"SLF4J-LOGGER","user_id":"81e517bb-c69b-4187-a6e9-9911c427994c","trace_id":"bd317853-2b88-43d3-84dc-109e7e0eba70","timestamp":"2023-05-15T20:14:20.76863+02:00 ","level":"WARN","thread":"zio-fiber-9","message":"Test WARNING!"}
{"name":"SLF4J-LOGGER","user_id":"844f97ef-7f09-469b-9f4b-765887beea9a","trace_id":"bd317853-2b88-43d3-84dc-109e7e0eba70","timestamp":"2023-05-15T20:14:20.768628+02:00","level":"WARN","thread":"zio-fiber-10","message":"Test WARNING!"}
{"name":"zio.logging.slf4j.bridge.Slf4jBridgeExampleApp","timestamp":"2023-05-15T20:14:20.777529+02:00","level":"DEBUG","thread":"zio-fiber-6","message":"Done"}
```

## Feature changes

### Version 2.1.9

SLF4J logger name is stored in log annotation with key `logger_name` (`zio.logging.loggerNameAnnotationKey`), 
in previous versions, logger name was stored in log annotation with key `slf4j_logger_name` (`Slf4jBridge.loggerNameAnnotationKey`),
for backward compatibility, if there is need to use legacy annotation key, it can be done with following initialisation

```scala
import zio.logging.slf4j.Slf4jBridge

program.provideCustom(Slf4jBridge.initialize(Slf4jBridge.loggerNameAnnotationKey))
```

NOTE: this feature may be removed in future versions

Following log format

```scala
import zio.logging.slf4j.Slf4jBridge
import zio.logging.LoggerNameExtractor

val loggerName = LoggerNameExtractor.annotationOrTrace(Slf4jBridge.loggerNameAnnotationKey)
val loggerNameFormat = loggerName.toLogFormat()
```
may be used to get logger name from log annotation or ZIO Trace.


This logger name extractor can be used also in log filter, which applying log filtering by defined logger name and level:

```scala
val logFilter: LogFilter[String] = LogFilter.logLevelByGroup(
  LogLevel.Info,
  loggerName.toLogGroup(),
  "zio.logging.slf4j" -> LogLevel.Debug,
  "SLF4J-LOGGER"      -> LogLevel.Warning
)
```

### Version 2.2.0

Deprecated log annotation with key `slf4j_logger_name` (`Slf4jBridge.loggerNameAnnotationKey`) removed, 
only common log annotation with key `logger_name` (`zio.logging.loggerNameAnnotationKey`) for logger name is supported now.


