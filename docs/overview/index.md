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

### Slf4j logger

`slf4j` logger layer:

```scala
import zio.logging.backend.SLF4J

val logger = Runtime.removeDefaultLoggers >>> SLF4J.slf4j
```

Default `slf4j` logger setup:
* logger name (by default)  is extracted from `zio.Trace`
    * for example, trace `zio.logging.example.Slf4jAnnotationApp.run(Slf4jSimpleApp.scala:17)` will have `zio.logging.example.Slf4jSimpleApp` as logger name
    * NOTE: custom logger name may be set by `SLF4J.loggerName` aspect
* all annotations are placed into MDC context
* cause is logged as throwable

Custom slf4j logger name set by aspect:

```scala
ZIO.logInfo("Starting user operation") @@ SLF4J.loggerName("zio.logging.example.UserOperation")
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
      LogFormat.default + LogFormat.annotation(LogAnnotation.TraceId) + LogFormat.annotation(
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


### Slf4j logger name and annotations

```scala
package zio.logging.example

import zio.logging.LogAnnotation
import zio.logging.backend.SLF4J
import zio.{ ExitCode, Runtime, Scope, ZIO, ZIOAppDefault, _ }

import java.util.UUID

object Slf4jSimpleApp extends ZIOAppDefault {

  private val logger = Runtime.removeDefaultLoggers >>> SLF4J.slf4j

  private val users = List.fill(2)(UUID.randomUUID())

  override def run: ZIO[Scope, Any, ExitCode] =
    (for {
      _       <- ZIO.logInfo("Start")
      traceId <- ZIO.succeed(UUID.randomUUID())
      _       <- ZIO.foreachPar(users) { uId =>
        {
          ZIO.logInfo("Starting user operation") *>
            ZIO.sleep(500.millis) *>
            ZIO.logInfo("Stopping user operation")
        } @@ ZIOAspect.annotated("user", uId.toString)
      } @@ LogAnnotation.TraceId(traceId) @@ SLF4J.loggerName("zio.logging.example.UserOperation")
      _       <- ZIO.logInfo("Done")
    } yield ExitCode.success).provide(logger)

}

```

Logback configuration:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<configuration>
    <appender name="STDOUT" class="ch.qos.logback.core.ConsoleAppender">
        <layout class="ch.qos.logback.classic.PatternLayout">
            <Pattern>%d{HH:mm:ss.SSS} [%thread] trace_id=%X{trace_id} user_id=%X{user} %-5level %logger{36} %msg%n</Pattern>
        </layout>
    </appender>
    <root level="debug">
        <appender-ref ref="STDOUT" />
    </root>
</configuration>
```

Expected Console Output:
```
20:52:39.271 [ZScheduler-Worker-10] trace_id= user_id= INFO  zio.logging.example.Slf4jSimpleApp Start
20:52:39.307 [ZScheduler-Worker-9] trace_id=170754d9-51a6-4916-beff-0a26c97e01dd user_id=c9b688b6-5fa1-4ea8-a8f0-e0b20a1cf07f INFO  zio.logging.example.UserOperation Starting user operation
20:52:39.307 [ZScheduler-Worker-8] trace_id=170754d9-51a6-4916-beff-0a26c97e01dd user_id=74d16bcd-6f62-45fd-95d6-0319d1524fe8 INFO  zio.logging.example.UserOperation Starting user operation
20:52:39.840 [ZScheduler-Worker-13] trace_id=170754d9-51a6-4916-beff-0a26c97e01dd user_id=74d16bcd-6f62-45fd-95d6-0319d1524fe8 INFO  zio.logging.example.UserOperation Stopping user operation
20:52:39.840 [ZScheduler-Worker-2] trace_id=170754d9-51a6-4916-beff-0a26c97e01dd user_id=c9b688b6-5fa1-4ea8-a8f0-e0b20a1cf07f INFO  zio.logging.example.UserOperation Stopping user operation
20:52:39.846 [ZScheduler-Worker-3] trace_id= user_id= INFO  zio.logging.example.Slf4jSimpleApp Done
```


### Slf4j bridge
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