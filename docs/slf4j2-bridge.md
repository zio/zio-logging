---
id: slf4j2-bridge
title: "SLF4J v2 bridge"
---

You can use `zio-logging` with SLF4J loggers, which is particularly useful for third-party non-ZIO libraries. To get started, add the `zio-logging-slf4j2-bridge` module to your project. This version is compatible with [SLF4J v2](https://www.slf4j.org/faq.html#changesInVersion200) (using JDK9+ module system ([JPMS](http://openjdk.java.net/projects/jigsaw/spec/)))

```scala
libraryDependencies += "dev.zio" %% "zio-logging-slf4j2-bridge" % "@VERSION@"
```

and use one of the `Slf4jBridge` layers when setting up logging:

```scala
import zio.logging.slf4j.Slf4jBridge

program.provideCustom(Slf4jBridge.init())
```

## Available Layers

The `Slf4jBridge` provides several initialization methods:

* `Slf4jBridge.init(configPath: NonEmptyChunk[String] = zio.logging.loggerConfigPath)` - Set up with `LogFilter` from [filter configuration](log-filter.md#configuration) and `Slf4jBridgeConfig`. The default configuration path is `logger`, and the default `LogLevel` is `INFO`.
* `Slf4jBridge.init(filter: LogFilter[Any])` - Set up with a specified `LogFilter` and the default `Slf4jBridgeConfig`.
* `Slf4jBridge.init(filter: LogFilter[Any], config: Slf4jBridgeConfig)` - Set up with both a specified `LogFilter` and `Slf4jBridgeConfig`.
* `Slf4jBridge.initialize` - Set up without any filtering.

SLF4J v2 [key-value pairs](https://www.slf4j.org/manual.html#fluent) are propagated like ZIO log annotations.

## Configuration

The SLF4J bridge can be configured using `Slf4jBridgeConfig` with the following options:

### Configuration Options

- **fiberRefPropagation** (default: `true`):
    - When enabled, propagates ZIO's fiber context (including annotations and log spans) to the SLF4J bridge logger.
    - This allows log annotations and spans to appear in the log output.
    - Note: Enabling this feature may impact performance.

- **loggerNameLogSpan** (default: `true`):
    - When enabled, includes the SLF4J logger name as a log span in the ZIO logging context.
    - This helps with log correlation between ZIO and SLF4J loggers.

### Configuration Example

You can configure these settings using ZIO's `ConfigProvider`. Here's an example configuration in HOCON format:

```hocon
logger {
  # Log filter configuration
  filter {
    # See filter configuration for more options
    rootLevel = INFO
  }
  
  # SLF4J bridge specific settings
  bridge {
    fiberRefPropagation = true
    loggerNameLogSpan = false
  }
}
```

For more information about filter configuration, see the [filter configuration](log-filter.md#configuration) documentation.

## Performance Considerations

### LogFilter and Performance

SLF4J libraries often use conditional logging with methods like [isTraceEnabled()](https://github.com/qos-ch/slf4j/blob/master/slf4j-api/src/main/java/org/slf4j/Logger.java#L170) to optimize performance. The SLF4J bridge provides several optimizations:

- Efficiently filtering log messages at the ZIO logging level
- Only evaluating message parameters when the log level is enabled
- Providing fine-grained control over logging behavior through configuration

### Logger Name Handling

The SLF4J logger name is stored in the log annotation with the key `logger_name` (`zio.logging.loggerNameAnnotationKey`). You can use the following format to work with logger names:

```scala
import zio.logging.slf4j.Slf4jBridge
import zio.logging.LoggerNameExtractor

// Extract logger name from log annotation or ZIO trace
val loggerName = LoggerNameExtractor.loggerNameAnnotationOrTrace

// Convert to log format
val loggerNameFormat = loggerName.toLogFormat()
```

## Setting Up a Custom Logger

You can set up the SLF4J bridge with a custom logger as follows:

```scala
import zio.logging.slf4j.Slf4jBridge
import zio.logging.consoleJsonLogger

val logger = Runtime.removeDefaultLoggers >>> consoleJsonLogger() >+> Slf4jBridge.init()
```

<br/>

**NOTE:** You should either use `zio-logging-slf4j2` to send all ZIO logs to an SLF4j provider (such as logback, log4j etc) OR `zio-logging-slf4j2-bridge` to send all SLF4j logs to
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
  ) + LogFormat.allAnnotations(Set(zio.logging.loggerNameAnnotationKey)) + LogFormat.default

  private val loggerConfig = ConsoleLoggerConfig(logFormat, logFilterConfig)

  override val bootstrap: ZLayer[ZIOAppArgs, Any, Any] =
    Runtime.removeDefaultLoggers >>> consoleJsonLogger(loggerConfig) >+> Slf4jBridge.init(loggerConfig.toFilter)

  private val uuids = List.fill(2)(UUID.randomUUID())

  override def run: ZIO[Scope, Any, ExitCode] =
    for {
      _ <- ZIO.logInfo("Start")
      _ <- ZIO.foreachPar(uuids) { u =>
        ZIO.succeed(slf4jLogger.info("Test {}!", "INFO")) *> ZIO.succeed(
          slf4jLogger.atWarn().addArgument("WARNING").log("Test {}!")
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
{"name":"zio.logging.example.Slf4jBridgeExampleApp","timestamp":"2024-05-28T08:05:48.056029+02:00","level":"INFO","thread":"zio-fiber-735317063","message":"Start"}
{"name":"SLF4J-LOGGER","trace_id":"416f3886-717c-4359-9f6e-c260e49fd158","user_id":"580376aa-f851-43a2-8b17-9456ce005e93","timestamp":"2024-05-28T08:05:48.065133+02:00","level":"WARN","thread":"zio-fiber-1749629949","message":"Test WARNING!"}
{"name":"SLF4J-LOGGER","trace_id":"416f3886-717c-4359-9f6e-c260e49fd158","user_id":"76d49e0d-7c28-47fd-b5a1-d05afb487cfe","timestamp":"2024-05-28T08:05:48.065174+02:00","level":"WARN","thread":"zio-fiber-697454589","message":"Test WARNING!"}
{"name":"zio.logging.example.Slf4jBridgeExampleApp","timestamp":"2024-05-28T08:05:48.068127+02:00","level":"DEBUG","thread":"zio-fiber-735317063","message":"Done"}
```
