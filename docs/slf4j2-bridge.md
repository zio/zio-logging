---
id: slf4j2-bridge
title: "SLF4J v2 bridge"
---

It is possible to use `zio-logging` for SLF4J loggers, usually third-party non-ZIO libraries. To do so, import  the `zio-logging-slf4j2-bridge` module for [SLF4J v2](https://www.slf4j.org/faq.html#changesInVersion200) (using JDK9+ module system ([JPMS](http://openjdk.java.net/projects/jigsaw/spec/)))

```scala
libraryDependencies += "dev.zio" %% "zio-logging-slf4j2-bridge" % "@VERSION@"
```

and use one of the `Slf4jBridge` layers when setting up logging:

```scala
import zio.logging.slf4j.Slf4jBridge

program.provideCustom(Slf4jBridge.init())
```

`Slf4jBridge` layers:
* `Slf4jBridge.init(configPath: NonEmptyChunk[String] = logFilterConfigPath)` - setup with `LogFilter` from [filter configuration](log-filter.md#configuration), default configuration path: `logger.filter`, default `LogLevel` is `INFO`
* `Slf4jBridge.init(filter: LogFilter[Any])` - setup with given `LogFilter`
* `Slf4jBridge.initialize` - setup without filtering

Need for log filtering in slf4j bridge: libraries with slf4j loggers, may have conditional logic for logging,
which using functions like [org.slf4j.Logger.isTraceEnabled()](https://github.com/qos-ch/slf4j/blob/master/slf4j-api/src/main/java/org/slf4j/Logger.java#L170).
logging parts may contain message and log parameters construction, which may be expensive and degrade performance of application.

<br/>

SLF4J logger name is stored in log annotation with key `logger_name` (`zio.logging.loggerNameAnnotationKey`), following log format

```scala
import zio.logging.slf4j.Slf4jBridge
import zio.logging.LoggerNameExtractor

val loggerName = LoggerNameExtractor.loggerNameAnnotationOrTrace
val loggerNameFormat = loggerName.toLogFormat()
```
may be used to get logger name from log annotation or ZIO Trace. 

This logger name extractor is used by default in log filter, which applying log filtering by defined logger name and level:

```scala
val logFilterConfig = LogFilter.LogLevelByNameConfig(
  LogLevel.Info,
  "zio.logging.slf4j" -> LogLevel.Debug,
  "SLF4J-LOGGER"      -> LogLevel.Warning
)

val logFilter: LogFilter[String] = logFilterConfig.toFilter
```
<br/>

SLF4J bridge with custom logger can be setup:

```scala
import zio.logging.slf4j.Slf4jBridge
import zio.logging.consoleJsonLogger

val logger = Runtime.removeDefaultLoggers >>> consoleJsonLogger() >+> Slf4jBridge.init()
```

<br/>

**NOTE:** You should either use `zio-logging-slf4j` to send all ZIO logs to an SLF4j provider (such as logback, log4j etc) OR `zio-logging-slf4j-bridge` to send all SLF4j logs to
ZIO logging. Enabling both causes circular logging and makes no sense.


## Examples

You can find the source code [here](https://github.com/zio/zio-logging/tree/master/examples)

### SLF4J bridge with JSON console logger

[//]: # (TODO: make snippet type-checked using mdoc)

```scala
package zio.logging.example

import zio.logging.{ ConsoleLoggerConfig, LogAnnotation, LogFilter, LogFormat, LoggerNameExtractor, consoleJsonLogger }
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
    Runtime.removeDefaultLoggers >>> consoleJsonLogger(loggerConfig) >+> Slf4jBridge.init(loggerConfig.toFilter)

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
```

Expected Console Output:
```
{"name":"zio.logging.slf4j.bridge.Slf4jBridgeExampleApp","timestamp":"2024-02-16T08:10:45.373807+01:00","level":"INFO","thread":"zio-fiber-6","message":"Start"}
{"name":"SLF4J-LOGGER","user_id":"d13f90ad-6b0a-45fd-bf94-1db7a0d8c0b7","trace_id":"561300a9-e6f1-4f61-8dcc-dfef476dab20","timestamp":"2024-02-16T08:10:45.421448+01:00","level":"WARN","thread":"zio-fiber-10","message":"Test WARNING!"}
{"name":"SLF4J-LOGGER","user_id":"0f28521f-ac8f-4d8e-beeb-13c85c90c041","trace_id":"561300a9-e6f1-4f61-8dcc-dfef476dab20","timestamp":"2024-02-16T08:10:45.421461+01:00","level":"WARN","thread":"zio-fiber-9","message":"Test WARNING!"}
{"name":"zio.logging.slf4j.bridge.Slf4jBridgeExampleApp","timestamp":"2024-02-16T08:10:45.428162+01:00","level":"DEBUG","thread":"zio-fiber-6","message":"Done"}
```
