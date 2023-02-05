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
- **Pluggable Backends** — Support multiple backends like ZIO Console, SLF4j, JS Console, JS HTTP endpoint.
- **Logger Context** — It has a first citizen _Logger Context_ implemented on top of `FiberRef`. The Logger Context maintains information like logger name, filters, correlation id, and so forth across different fibers. It supports _Mapped Diagnostic Context (MDC)_ which manages contextual information across fibers in a concurrent environment.
- Richly integrated into ZIO 2's built-in logging facilities
- ZIO Console, SLF4j, and other backends

## Installation

In order to use this library, we need to add the following line in our `build.sbt` file:

```scala
libraryDependencies += "dev.zio" %% "zio-logging" % "@VERSION@"
```

There are also some optional dependencies:

```scala
// JPL integration
libraryDependencies += "dev.zio" %% "zio-logging-jpl" % "@VERSION@"

// SLF4j v1 integration
libraryDependencies += "dev.zio" %% "zio-logging-slf4j" % "@VERSION@"

// SLF4j v2 integration
libraryDependencies += "dev.zio" %% "zio-logging-slf4j2" % "@VERSION@"

// Using ZIO Logging for SLF4j v1 loggers, usually third-party non-ZIO libraries
libraryDependencies += "dev.zio" %% "zio-logging-slf4j-bridge" % "@VERSION@"

// Using ZIO Logging for SLF4j v2 loggers, usually third-party non-ZIO libraries
libraryDependencies += "dev.zio" %% "zio-logging-slf4j2-bridge" % "@VERSION@"
```

## Example

Let's try an example of ZIO Logging which demonstrates a simple application of ZIO logging.

The recommended place for setting the logger is application boostrap. In this case, custom logger will be set for whole application runtime (also application failures will be logged with specified logger).

[//]: # (TODO: make snippet type-checked using mdoc)

```scala
import zio.logging.{ LogFormat, console }
import zio.{ ExitCode, Runtime, Scope, ZIO, ZIOAppArgs, ZIOAppDefault, ZLayer }

object SimpleApp extends ZIOAppDefault {

  override val bootstrap: ZLayer[ZIOAppArgs, Any, Any] =
    Runtime.removeDefaultLoggers >>> console(LogFormat.default)

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
timestamp=2022-10-28T18:40:25.517623+02:00 level=INFO thread=zio-fiber-6 message="Start"
timestamp=2022-10-28T18:40:25.54676+02:00  level=ERROR thread=zio-fiber-0 message="" cause=Exception in thread "zio-fiber-6" java.lang.String: FAILURE
	at zio.logging.example.SimpleApp.run(SimpleApp.scala:14)
```

You can find the source code of examples [here](https://github.com/zio/zio-logging/tree/master/examples)
