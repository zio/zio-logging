---
id: overview_index
title: "Summary"
---

ZIO Logging is a functional, type-safe library for logging.

## Installation

`ZIO-Logging` is available via maven repo so importing in `build.sbt` is sufficient:

```scala
libraryDependencies += "dev.zio" %% "zio-logging" % version
```

If you need `slf4j` integration use `zio-logging-slf4j` instead: 

```scala
libraryDependencies += "dev.zio" %% "zio-logging-slf4j" % version
```

If you need  `scala.js console` integration use `zio-logging-jsconsole` instead: 

```scala
libraryDependencies += "dev.zio" %%% "zio-logging-jsconsole" % version
```

If you need  `scala.js http` log publishing integration use `zio-logging-jshttp` instead: 

```scala
libraryDependencies += "dev.zio" %%% "zio-logging-jshttp" % version
```

### Logger Context

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

### Simple console log

```scala
package zio.logging.example

import zio.logging.LogFormat
import zio.logging.backend.SLF4J
import zio.{ ExitCode, LogLevel, Runtime, Scope, ZIO, ZIOAppDefault }

object Slf4jSimpleApp extends ZIOAppDefault {

  private val slf4jLogger =
    Runtime.removeDefaultLoggers >>> SLF4J.slf4j(LogLevel.Info, LogFormat.line |-| LogFormat.cause)

  override def run: ZIO[Scope, Any, ExitCode] =
    ZIO.logInfo("Hello from ZIO logger").provide(slf4jLogger).as(ExitCode.success)

}

```

Expected console output:

```
20:16:06.700 [ZScheduler-Worker-12] INFO  zio-slf4j-logger Hello from ZIO logger
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

  private val slf4jLogger =
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
    } yield ExitCode.success).provide(slf4jLogger)

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

### Slf4j and MDC
We can create a logger and define a number of annotations that will be translated into an MDC context:

```scala
object Slf4jMdc extends zio.App {

  val userId = LogAnnotation[UUID](
    name = "user-id",
    initialValue = UUID.fromString("0-0-0-0-0"),
    combine = (_, newValue) => newValue,
    render = _.toString
  )

  val logLayer = Slf4jLogger.makeWithAnnotationsAsMdc(List(userId))
  val users = List.fill(2)(UUID.randomUUID())

  override def run(args: List[String]): ZIO[zio.ZEnv, Nothing, Int] =
    (for {
      correlationId <- UIO(Some(UUID.randomUUID()))
      _ <- ZIO.foreachPar(users) { uId =>
        log.locally(_.annotate(userId, uId).annotate(LogAnnotation.CorrelationId, correlationId)) {
          log.info("Starting operation") *>
            ZIO.sleep(500.millis) *>
            log.info("Stopping operation")
        }
      }
    } yield ExitCode.success).provideCustomLayer(logLayer)
}
```

Expected console output (with logstash encoder):

```
{"@timestamp":"2020-04-26T13:19:14.845+02:00","@version":"1","message":"Starting operation","logger_name":"zio.logging.Slf4jMdc$","thread_name":"zio-default-async-7-1282747478","level":"INFO","level_value":20000,"user-id":"952fd569-b63c-4dac-ac9a-63dd2c60e50e"}
{"@timestamp":"2020-04-26T13:19:14.845+02:00","@version":"1","message":"Starting operation","logger_name":"zio.logging.Slf4jMdc$","thread_name":"zio-default-async-8-1282747478","level":"INFO","level_value":20000,"user-id":"ec86bf22-41b4-4d09-a2b7-6d8ccadb1ca0"}
{"@timestamp":"2020-04-26T13:19:15.360+02:00","@version":"1","message":"Stopping operation","logger_name":"zio.logging.Slf4jMdc$","thread_name":"zio-default-async-11-1282747478","level":"INFO","level_value":20000,"user-id":"952fd569-b63c-4dac-ac9a-63dd2c60e50e"}
{"@timestamp":"2020-04-26T13:19:15.360+02:00","@version":"1","message":"Stopping operation","logger_name":"zio.logging.Slf4jMdc$","thread_name":"zio-default-async-10-1282747478","level":"INFO","level_value":20000,"user-id":"ec86bf22-41b4-4d09-a2b7-6d8ccadb1ca0"}
```

### Scala.js Console

The Scala.js console works as the JVM Console using the standard JS console for output.

```scala
import zio.logging._
import zio.logging.js._
...

    Logging.log("test") *>
      Logging.log(LogLevel.Trace)("test Trace") *>
      Logging.log(LogLevel.Debug)("test Debug") *>
      Logging.log(LogLevel.Info)("test Info") *>
      Logging.log(LogLevel.Warn)("test Warn") *>
      Logging.log(LogLevel.Error)("test Error") *>
      Logging.log(LogLevel.Fatal)("test Fatal") *>
      Logging.log(LogLevel.Off)("test Off")
```


Expected Console Output:
```
1970-01-01T00:00Z INFO  test
Trace: 1970-01-01T00:00Z TRACE  test Trace
    at /Users/alberto/Projects/zio-logging/jsconsole/target/scala-2.12/zio-logging-jsconsole-test-fastopt.js:14731:82
    at $c_sjsr_AnonFunction0.apply__O (/Users/alberto/Projects/zio-logging/jsconsole/target/scala-2.12/zio-logging-jsconsole-test-fastopt.js:54402:23)
    at $c_Lzio_internal_FiberContext.evaluateNow__Lzio_ZIO__V (/Users/alberto/Projects/zio-logging/jsconsole/target/scala-2.12/zio-logging-jsconsole-test-fastopt.js:59390:42)
    at /Users/alberto/Projects/zio-logging/jsconsole/target/scala-2.12/zio-logging-jsconsole-test-fastopt.js:58797:13
    at $c_Lzio_internal_FiberContext$$Lambda$2.run__V (/Users/alberto/Projects/zio-logging/jsconsole/target/scala-2.12/zio-logging-jsconsole-test-fastopt.js:34590:16)
    at $c_sjs_concurrent_QueueExecutionContext$PromisesExecutionContext.scala$scalajs$concurrent$QueueExecutionContext$PromisesExecutionContext$$$anonfun$execute$2__sr_BoxedUnit__jl_Runnable__sjs_js_$bar (/Users/alberto/Projects/zio-logging/jsconsole/target/scala-2.12/zio-logging-jsconsole-test-fastopt.js:63008:16)
    at /Users/alberto/Projects/zio-logging/jsconsole/target/scala-2.12/zio-logging-jsconsole-test-fastopt.js:63025:24
    at processTicksAndRejections (internal/process/task_queues.js:97:5)
1970-01-01T00:00Z DEBUG  test Debug
1970-01-01T00:00Z INFO  test Info
1970-01-01T00:00Z WARN  test Warn
1970-01-01T00:00Z ERROR  test Error
1970-01-01T00:00Z FATAL  test Fatal
```

### Scala.js HTTP Ajax pusher

This Scala.js implementation allows you to send logs to a remote server. It's very useful to control the navigation and action inside a SPA.

All events are sent to a backend via Ajax POST.

The JSON format of the event is the following:

```
{
     "date": "2020-03-15T21:58:31.085+01:00",
     "clientId": "446352f6-11be-4af9-99cb-2c0c1bab8721",
     "level": "info",
     "name": "index_page",
     "msg": "index page loaded",
     "cause": ""
}
```

The **clientId** is an identifier for the connection. The default is a randomly generated UUID.

To create a logger, the **url** for the POST is mandatory. 


```scala


import zio.logging._
import zio.logging.js._
...

val loggerLayer = HTTPLogger.make("http://localhost:9000/event/collect")((context, message) => message)


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