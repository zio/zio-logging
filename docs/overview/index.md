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
Logger Context is a mechanism that we use to carry information like logger name or correlation id across different fibers. The implementation uses `FiberRef` from `ZIO`. 

```scala
import zio.logging._
log.locally(LogAnnotation.Name("my-logger" :: Nil)) {
  log.info("log entry") // value of LogAnnotation.Name is only visible in this block
}
```

The user of the library is allowed to add a custom `LogAnnotation`: 

```scala
val customLogAnnotation = LogAnnotation("custom_annotation", 1, _ + _, _.toString)
```

## Examples

### Simple console log

```scala
import zio.logging._

object Simple extends zio.App {

  val env =
    Logging.console(
      logLevel = LogLevel.Info,
      format = LogFormat.ColoredLogFormat()
    ) >>> Logging.withRootLoggerName("my-component")

  override def run(args: List[String]) =
    log.info("Hello from ZIO logger").provideCustomLayer(env).as(ExitCode.success)
}

```

Expected console output:

```
2020-02-02T18:09:45.197-05:00 INFO default-logger Hello from ZIO logger
```

### Logger Name and Log Level

```scala
import zio.logging._

object LogLevelAndLoggerName extends zio.App {

  val env =
    Logging.consoleErr()

  override def run(args: List[String]) =
    log.locally(LogAnnotation.Name("logger-name-here" :: Nil)) {
      log.debug("Hello from ZIO logger")
    }.provideCustomLayer(env).as(ExitCode.success)
}
```

Expected console output:

```
2020-02-02T18:22:33.200-05:00 DEBUG logger-name-here Hello from ZIO logger
```

### Slf4j and correlation id
We can create an `slf4j` logger and define how the annotations translate into the logging message:

```scala

import zio.logging._
import zio.logging.slf4j._


object Slf4jAndCorrelationId extends zio.App {
  val logFormat = "[correlation-id = %s] %s"

  val env =
    Slf4jLogger.make{(context, message) => 
        val correlationId = LogAnnotation.CorrelationId.render(
          context.get(LogAnnotation.CorrelationId)
        )
        logFormat.format(correlationId, message)
    }

  def generateCorrelationId =
    Some(java.util.UUID.randomUUID())

  override def run(args: List[String]) =
      (for {
        fiber <- log.locally(correlationId("1234"))(ZIO.unit).fork
        _     <- log.info("info message without correlation id")
        _     <- fiber.join
        _ <- log.locally(correlationId("1234111")) {
              log.info("info message with correlation id") *>
                log.throwable("another info message with correlation id", new RuntimeException("error message")).fork
            }
      } yield ExitCode.success).provideLayer(env)
}

```


Expected Console Output:
```
00:27:56.448 [zio-default-async-1-1920387277] INFO  zio.logging.Examples$ [correlation-id = undefined-correlation-id] info message without correlation id
00:27:56.530 [zio-default-async-1-1920387277] INFO  zio.logging.Examples$ [correlation-id = 1234111] info message with correlation id
00:27:56.546 [zio-default-async-4-1920387277] ERROR zio.logging.Examples$ [correlation-id = 1234111] another info message with correlation id
Fiber failed.
An unchecked error was produced.
java.lang.RuntimeException: error message
	at zio.logging.Examples$.$anonfun$run$6(Examples.scala:26)
	at zio.ZIO$ZipRightFn.apply(ZIO.scala:3368)
	at zio.ZIO$ZipRightFn.apply(ZIO.scala:3365)
	at zio.internal.FiberContext.evaluateNow(FiberContext.scala:815)
	at zio.internal.FiberContext.$anonfun$fork$2(FiberContext.scala:681)
	at java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1142)
	at java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:617)
	at java.lang.Thread.run(Thread.java:745)
No ZIO Trace available.
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

and use the `bindSlf4jBridge` layer when setting up logging:

```scala
import zio.logging.slf4j.bridge.bindSlf4jBridge

val env = Logging.consoleErr() >>> bindSlf4jBridge
```