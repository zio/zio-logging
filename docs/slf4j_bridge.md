---
id: slf4j_bridge
title: "SLF4J bridge"
---

It is possible to use `zio-logging` for SLF4j loggers, usually third-party non-ZIO libraries. To do so, import
the `zio-logging-slf4j-bridge` module:

```scala
libraryDependencies += "dev.zio" %% "zio-logging-slf4j-bridge" % @VERSION@
```

and use the `Slf4jBridge.initialize` layer when setting up logging:

```scala
import zio.logging.slf4j.Slf4jBridge

program.provideCustom(Slf4jBridge.initialize)
```

SLF4J bridge with custom logger can be setup:

```scala
import zio.logging.slf4j.Slf4jBridge

val logger = Runtime.removeDefaultLoggers >>> zio.logging.consoleJson(LogFormat.default, LogLevel.Debug) >+> Slf4jBridge.initialize
```

**NOTE** You should either use `zio-logging-slf4j` to send all ZIO logs to an SLF4j provider (such as logback, log4j etc) OR `zio-logging-slf4j-bridge` to send all SLF4j logs to
ZIO logging. Enabling both causes circular logging and makes no sense.


## Examples

### SLF4J bridge with JSON console logger

```scala
package zio.logging.slf4j.bridge

import org.slf4j.{ Logger, LoggerFactory }
import zio.logging.{ LogFormat, consoleJson }
import zio.{ ExitCode, LogLevel, Runtime, Scope, ZIO, ZIOAppArgs, ZIOAppDefault, ZLayer }

object Slf4jBridgeExampleApp extends ZIOAppDefault {

  private val slf4jLogger: Logger = LoggerFactory.getLogger("SLF4J-LOGGER")

  override val bootstrap: ZLayer[ZIOAppArgs, Any, Any] =
    Runtime.removeDefaultLoggers >>> consoleJson(LogFormat.default, LogLevel.Debug) >+> Slf4jBridge.initialize

  override def run: ZIO[Scope, Any, ExitCode] =
    (for {
      _ <- ZIO.logInfo("Start")
      _ <- ZIO.succeed(slf4jLogger.warn("Test {}!", "WARNING"))
      _ <- ZIO.logDebug("Done")
    } yield ExitCode.success)

}
```

Expected Console Output:
```
{"timestamp":"2022-10-28T18:06:53.835377+02:00","level":"INFO","thread":"zio-fiber-8","message":"Start"}
{"timestamp":"2022-10-28T18:06:53.855229+02:00","level":"WARN","thread":"zio-fiber-9","message":"Test WARNING!"}
{"timestamp":"2022-10-28T18:06:53.858792+02:00","level":"DEBUG","thread":"zio-fiber-8","message":"Done"}
```
