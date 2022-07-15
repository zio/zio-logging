---
id: overview_index
title: "Summary"
---

_ZIO Logging_ is the official logging library for ZIO 2 applications, with integrations for common logging backends.

- Type-safe, purely-functional, ZIO-powered
- Compositional, type-safe log formatting
- Richly integrated into ZIO 2's built-in logging facilities
- ZIO Console, SLF4j, and other backends

## Installation

`ZIO-Logging` is available via maven repo so importing in `build.sbt` is sufficient:

```scala
libraryDependencies += "dev.zio" %% "zio-logging" % version
```

If you need `slf4j` integration use `zio-logging-slf4j` instead: 

```scala
libraryDependencies += "dev.zio" %% "zio-logging-slf4j" % version
```

### Log Format

A `LogFormat` represents a DSL to describe the format of text log messages.

```scala
import zio.logging.console
import zio.logging.LogFormat._

val myLogFormat = timestamp.fixed(32) |-| level |-| label("message", quoted(line))
val myConsoleLogger = console(myLogFormat)
```

### Logger Context and Annotations

The `logContext` fiber reference is used to store typed, structured log
annotations, which can be utilized by backends to enrich log messages.

Because `logContext` is an ordinary `zio.FiberRef`, it may be get, set,
and updated like any other fiber reference. However, the idiomatic way to
interact with `logContext` is by using `zio.logging.LogAnnotation`.

For example:

```scala
myResponseHandler(request) @@ LogAnnotation.UserId(request.userId)
```

This code would add the structured log annotation `LogAnnotation.UserId`
to all log messages emitted by the `myResponseHandler(request)` effect.

The user of the library is allowed to add a custom `LogAnnotation`: 

```scala
import zio.logging.LogAnnotation

val customLogAnnotation = LogAnnotation[Int]("custom_annotation", _ + _, _.toString)
```

## Examples

You can find the source code [here](https://github.com/zio/zio-logging/tree/master/examples/src/main/scala/zio/logging/example)

### Simple console log

```scala
package zio.logging.example

import zio.logging.{ LogFormat, console }
import zio.{ ExitCode, Runtime, Scope, ZIO, ZIOAppDefault }

object ConsoleSimpleApp extends ZIOAppDefault {

  private val logger =
    Runtime.removeDefaultLoggers >>> console(LogFormat.default)

  override def run: ZIO[Scope, Any, ExitCode] =
    ZIO.logInfo("Hello from ZIO logger").provide(logger).as(ExitCode.success)

}
```

Expected console output:

```
timestamp=2022-07-15T20:48:37.106927+02:00 level=INFO thread=zio-fiber-6 message="Hello from ZIO logger"
```

### JSON console log

```scala
package zio.logging.example

import zio.logging.{ LogAnnotation, LogFormat, consoleJson }
import zio.{ ExitCode, Runtime, Scope, ZIO, ZIOAppDefault, _ }

import java.util.UUID

object ConsoleJsonApp extends ZIOAppDefault {

  private val userLogAnnotation = LogAnnotation[UUID]("user", (_, i) => i, _.toString)

  private val logger =
    Runtime.removeDefaultLoggers >>> consoleJson(
      LogFormat.default |-| LogFormat.annotation(LogAnnotation.TraceId) |-| LogFormat.annotation(
        userLogAnnotation
      )
    )

  private val users = List.fill(2)(UUID.randomUUID())

  override def run: ZIO[Scope, Any, ExitCode] =
    (for {
      traceId <- ZIO.succeed(UUID.randomUUID())
      _       <- ZIO.foreachPar(users) { uId =>
        {
          ZIO.logInfo("Starting operation") *>
            ZIO.sleep(500.millis) *>
            ZIO.logInfo("Stopping operation")
        } @@ userLogAnnotation(uId)
      } @@ LogAnnotation.TraceId(traceId)
      _       <- ZIO.logInfo("Done")
    } yield ExitCode.success).provide(logger)

}
```

Expected console output:

```
{"timestamp":"2022-07-15T20:19:03.009677+02:00","level":"INFO","thread":"zio-fiber-8","message":"Starting operation","trace_id":"19e74a1f-c910-42e5-b060-8a0024baf3b8","user":"06f6eb07-b828-4f40-8cce-1853971e3ec3"}
{"timestamp":"2022-07-15T20:19:03.009638+02:00","level":"INFO","thread":"zio-fiber-7","message":"Starting operation","trace_id":"19e74a1f-c910-42e5-b060-8a0024baf3b8","user":"2e1930a4-4efb-4f36-a021-b55248b4f20e"}
{"timestamp":"2022-07-15T20:19:03.557638+02:00","level":"INFO","thread":"zio-fiber-7","message":"Stopping operation","trace_id":"19e74a1f-c910-42e5-b060-8a0024baf3b8","user":"2e1930a4-4efb-4f36-a021-b55248b4f20e"}
{"timestamp":"2022-07-15T20:19:03.557595+02:00","level":"INFO","thread":"zio-fiber-8","message":"Stopping operation","trace_id":"19e74a1f-c910-42e5-b060-8a0024baf3b8","user":"06f6eb07-b828-4f40-8cce-1853971e3ec3"}
{"timestamp":"2022-07-15T20:19:03.566659+02:00","level":"INFO","thread":"zio-fiber-6","message":"Done"}
```

### Slf4j and annotations
We can create an `slf4j` logger and define how the annotations translate into the logging message:

```scala
package zio.logging.example

import zio.logging.{ LogAnnotation, LogFormat }
import zio.logging.backend.SLF4J
import zio.{ ExitCode, LogLevel, Runtime, Scope, ZIO, ZIOAppDefault }
import zio._

import java.util.UUID

object Slf4jAnnotationApp extends ZIOAppDefault {

  private val logger =
    Runtime.removeDefaultLoggers >>> SLF4J.slf4j(
      LogLevel.Info,
      LogFormat.annotation(LogAnnotation.TraceId) |-| LogFormat.annotation(
        "user"
      ) |-| LogFormat.line |-| LogFormat.cause
    )

  private val users = List.fill(2)(UUID.randomUUID())

  override def run: ZIO[Scope, Any, ExitCode] =
    (for {
      traceId <- ZIO.succeed(UUID.randomUUID())
      _       <- ZIO.foreachPar(users) { uId =>
        {
          ZIO.logInfo("Starting operation") *>
            ZIO.sleep(500.millis) *>
            ZIO.logInfo("Stopping operation")
        } @@ ZIOAspect.annotated("user", uId.toString)
      } @@ LogAnnotation.TraceId(traceId)
      _       <- ZIO.logInfo("Done")
    } yield ExitCode.success).provide(logger)

}
```

Expected Console Output:
```
20:40:28.256 [ZScheduler-Worker-13] INFO  zio-slf4j-logger trace_id=011ae21a-78b7-45a2-9b82-f5ceea62ec6b user=0b20ba98-b707-4e7a-8aeb-ad3751ae126f Starting operation 
20:40:28.257 [ZScheduler-Worker-13] INFO  zio-slf4j-logger trace_id=011ae21a-78b7-45a2-9b82-f5ceea62ec6b user=c395c22a-5672-4a11-bcae-766d0aeda382 Starting operation 
20:40:28.630 [ZScheduler-Worker-15] INFO  zio-slf4j-logger trace_id=011ae21a-78b7-45a2-9b82-f5ceea62ec6b user=0b20ba98-b707-4e7a-8aeb-ad3751ae126f Stopping operation 
20:40:28.758 [ZScheduler-Worker-3] INFO  zio-slf4j-logger trace_id=011ae21a-78b7-45a2-9b82-f5ceea62ec6b user=c395c22a-5672-4a11-bcae-766d0aeda382 Stopping operation 
20:40:28.763 [ZScheduler-Worker-10] INFO  zio-slf4j-logger   Done 
```


### SLF4j bridge
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

**NOTE** You should either use `zio-logging-slf4j` to send all ZIO logs to an SLF4j provider (such as logback, log4j etc) OR `zio-logging-slf4j-bridge` to send all SLF4j logs to 
ZIO logging. Enabling both causes circular logging and makes no sense.