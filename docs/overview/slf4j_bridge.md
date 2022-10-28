---
id: overview_slf4j_bridge
title: "SLF4J bridge"
---

## SLF4J bridge

It is possible to use `zio-logging` for SLF4j loggers, usually third-party non-ZIO libraries. To do so, import
the `zio-logging-slf4j-bridge` module:

```scala
libraryDependencies += "dev.zio" %% "zio-logging-slf4j-bridge" % version
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
