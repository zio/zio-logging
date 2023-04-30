---
id: index
title: "Introduction to ZIO Logging"
sidebar_label: "Introduction"
---

[ZIO Logging](https://github.com/zio/zio-logging) is simple logging for ZIO apps, with correlation, context, and pluggable backends out of the box with integrations for common logging backends.

@PROJECT_BADGES@

## Introduction

When we are writing our applications using ZIO effects, to log easy way we need a ZIO native solution for logging. ZIO Logging is an environmental effect for adding logging into our ZIO applications.

Key features of ZIO Logging:

- **ZIO Native** — Other than it is a type-safe and purely functional solution, it leverages ZIO's features.
- **Multi-Platform** - It supports both JVM and JS platforms.
- **Composable** — Loggers are composable together via contraMap.
- **Pluggable Backends** — Support multiple backends like ZIO Console, SLF4j, JPL.
- **Logger Context** — It has a first citizen _Logger Context_ implemented on top of `FiberRef`. The Logger Context maintains information like logger name, filters, correlation id, and so forth across different fibers. It supports _Mapped Diagnostic Context (MDC)_ which manages contextual information across fibers in a concurrent environment.
- Richly integrated into ZIO 2's built-in logging facilities
- ZIO Console, SLF4j, and other backends

## Installation

In order to use this library, we need to add the following line in our `build.sbt` file:

```scala
// ZIO Logging backends
libraryDependencies += "dev.zio" %% "zio-logging" % "@VERSION@"
```

The main module contains the following loggers implementations: 
* [console loggers](console-logger.md)
* [file loggers](file-logger.md)


### SLF4J Backend

If you like to use [`SLF4J`](https://www.slf4j.org/) logging backends (e.g. java.util.logging, logback, log4j) add the one of following lines, to your `build.sbt` file:

```scala
// SLF4j v1 integration
libraryDependencies += "dev.zio" %% "zio-logging-slf4j" % "@VERSION@"

// SLF4j v2 integration
libraryDependencies += "dev.zio" %% "zio-logging-slf4j2" % "@VERSION@"
```
See SLF4J [v2](slf4j2.md) or [v1](slf4j1.md) sections for more details.


### SLF4J Bridge

With this logging bridge, it is possible to use `zio-logging` for SLF4J loggers (usually third-party non-ZIO libraries), add the one of following lines, to your `build.sbt` file: 

```scala
// Using ZIO Logging for SLF4j v1 loggers, usually third-party non-ZIO libraries
libraryDependencies += "dev.zio" %% "zio-logging-slf4j-bridge" % "@VERSION@"

// Using ZIO Logging for SLF4j v2 loggers, usually third-party non-ZIO libraries
libraryDependencies += "dev.zio" %% "zio-logging-slf4j2-bridge" % "@VERSION@"
```

See SLF4J Bridge [v2](slf4j2-bridge.md) or [v1](slf4j1-bridge.md) sections for more details.


### Java Platform/System Logger Backend

If you like to use  [`Java Platform/System Logger`](https://openjdk.org/jeps/264) logging backend add the following line, to your `build.sbt` file:

```scala
// JPL integration
libraryDependencies += "dev.zio" %% "zio-logging-jpl" % "@VERSION@"
```

See [`Java Platform/System Logger`](jpl.md) section for more details.


## Example

Let's try an example of ZIO Logging which demonstrates a simple application of ZIO logging.

The recommended place for setting the logger is application boostrap. In this case, custom logger will be set for whole application runtime (also application failures will be logged with specified logger).

[//]: # (TODO: make snippet type-checked using mdoc)

```scala
package zio.logging.example

import zio.logging.consoleLogger
import zio.{ ExitCode, Runtime, Scope, ZIO, ZIOAppArgs, ZIOAppDefault, ZLayer }

object SimpleApp extends ZIOAppDefault {

  override val bootstrap: ZLayer[ZIOAppArgs, Any, Any] =
    Runtime.removeDefaultLoggers >>> consoleLogger()

  override def run: ZIO[Scope, Any, ExitCode] =
    for {
      _ <- ZIO.logInfo("Start")
      _ <- ZIO.fail("FAILURE")
      _ <- ZIO.logInfo("Done")
    } yield ExitCode.success

}
```

Expected console output:

```
timestamp=2023-03-15T08:36:24.421098+01:00 level=INFO thread=zio-fiber-4 message="Start"
timestamp=2023-03-15T08:36:24.440181+01:00 level=ERROR thread=zio-fiber-0 message="" cause=Exception in thread "zio-fiber-4" java.lang.String: FAILURE
	at zio.logging.example.SimpleApp.run(SimpleApp.scala:29)
```

You can find the source code of examples [here](https://github.com/zio/zio-logging/tree/master/examples)
