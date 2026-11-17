---
id: index
title: "Introduction to ZIO Logging"
sidebar_label: "Introduction"
---

[ZIO Logging](https://github.com/zio/zio-logging) is simple logging for ZIO apps, with correlation, context, and pluggable backends out of the box with integrations for common logging backends.

@PROJECT_BADGES@

## Introduction

When we are writing our applications using ZIO effects, to log easy way we need a ZIO native solution for logging.

Key features of ZIO Logging:

- **ZIO Native** — Other than it is a type-safe and purely functional solution, it leverages ZIO's features.
- **Multi-Platform** - The core module supports JVM, JS and Native. File loggers are JVM and Native only, and all other modules are JVM only. See [Platforms](#platforms).
- **Composable** — Loggers are composable together via contraMap.
- **Pluggable Backends** — Support multiple backends like ZIO Console, SLF4j, JPL.
- **Logger Context** — It has a first citizen _Logger Context_ implemented on top of `FiberRef`. The Logger Context maintains information like logger name, filters, correlation id, and so forth across different fibers. It supports _Mapped Diagnostic Context (MDC)_ which manages contextual information across fibers in a concurrent environment.
- Richly integrated into ZIO 2's built-in logging facilities
- ZIO Console, SLF4j, and other backends

## Installation

Add the core module to your `build.sbt`:

```scala
libraryDependencies += "dev.zio" %% "zio-logging" % "@VERSION@"
```

[Getting Started](getting-started.md) covers Scala.js and Scala Native, the SLF4J, JPL and java.util.logging modules, and when to use each one.

## Platforms

ZIO Logging is published for JVM, JS and Native. Which parts work on each platform depends on where the code lives in the source tree:

| Feature                                                                                            | JVM | JS  | Native | Why                                                |
| -------------------------------------------------------------------------------------------------- | --- | --- | ------ | -------------------------------------------------- |
| Console loggers (`consoleLogger`, `consoleJsonLogger`, `consoleErrLogger`, `consoleErrJsonLogger`) | yes | yes | yes    | shared code, writes to `System.out` / `System.err` |
| File loggers (`fileLogger`, `fileJsonLogger`, `fileAsyncLogger`, `fileAsyncJsonLogger`)            | yes | no  | yes    | JVM and Native code only                           |
| Log metrics (`logMetrics`), reconfigurable logger                                                  | yes | yes | yes    | shared code                                        |
| `LogFormat`, `LogFilter`, `LogAnnotation`                                                          | yes | yes | yes    | shared code                                        |
| SLF4J, SLF4J bridges, java.util.logging bridge, JPL                                                | yes | no  | no     | separate JVM-only modules                          |

## Example

Let's try an example of ZIO Logging which demonstrates a simple application of ZIO logging.

The recommended place for setting the logger is the application bootstrap. The logger is then set for the whole application runtime, so application failures are also logged with it. The following application installs the console logger in its bootstrap:

```scala mdoc
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

[Getting Started](getting-started.md) explains this program step by step. You can find the source code of more examples [here](https://github.com/zio/zio-logging/tree/master/examples).
