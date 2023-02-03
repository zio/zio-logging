---
id: slf4j2-bridge
title: "SLF4J v2 bridge"
---

It is possible to use `zio-logging` for SLF4J loggers, usually third-party non-ZIO libraries. To do so, import  the `zio-logging-slf4j2-bridge` module for [SLF4J v2](https://www.slf4j.org/faq.html#changesInVersion200) (using JDK9+ module system ([JPMS](http://openjdk.java.net/projects/jigsaw/spec/)))

```scala
libraryDependencies += "dev.zio" %% "zio-logging-slf4j2-bridge" % "@VERSION@"
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

This logger name extractor is used by default in log filter, which applying log filtering by defined logger name and level:

```scala
val logFilter: LogFilter[String] = LogFilter.logLevelByName(
  LogLevel.Info,
  "zio.logging.slf4j" -> LogLevel.Debug,
  "SLF4J-LOGGER"      -> LogLevel.Warning
)
```

<br/>

SLF4J bridge with custom logger can be setup:

```scala
import zio.logging.slf4j.Slf4jBridge

val logger = Runtime.removeDefaultLoggers >>> zio.logging.consoleJson(LogFormat.default, LogLevel.Debug) >+> Slf4jBridge.initialize
```

<br/>

**NOTE** You should either use `zio-logging-slf4j` to send all ZIO logs to an SLF4j provider (such as logback, log4j etc) OR `zio-logging-slf4j-bridge` to send all SLF4j logs to
ZIO logging. Enabling both causes circular logging and makes no sense.


## Examples

### SLF4J bridge with JSON console logger

[//]: # (TODO: make snippet type-checked using mdoc)

```scala
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
      _ <- ZIO.logInfo("Start")
      _ <- ZIO.succeed(slf4jLogger.warn("Test {}!", "WARNING"))
      _ <- ZIO.logDebug("Done")
    } yield ExitCode.success

}
```

Expected Console Output:
```
{"name":"zio.logging.slf4j.bridge.Slf4jBridgeExampleApp","timestamp":"2023-01-07T18:25:40.397593+01:00","level":"DEBUG","thread":"zio-fiber-4","message":"Start"}
{"name":"SLF4J-LOGGER","timestamp":"2023-01-07T18:25:40.416612+01:00","level":"WARN","thread":"zio-fiber-6","message":"Test WARNING!"}
{"name":"zio.logging.slf4j.bridge.Slf4jBridgeExampleApp","timestamp":"2023-01-07T18:25:40.42043+01:00 ","level":"INFO","thread":"zio-fiber-4","message":"Done"}
```
