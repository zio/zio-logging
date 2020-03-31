---
id: overview_index
title: "Summary"
---

ZIO Logging is a functional, type-safe library for logging.

## Installation

`ZIO-Logging` is available via maven repo so import in `build.sbt` is sufficient:

```scala
libraryDependencies += "dev.zio" %% "zio-logging" % version
```

If you need `slf4j` integration use `zio-logging-slf4j` instead 

```scala
libraryDependencies += "dev.zio" %% "zio-logging-slf4j" % version
```

If you need  `scala.js console` integration use `zio-logging-slf4j` instead 

```scala
libraryDependencies += "dev.zio" %% "zio-logging-jsconsole" % "0.2.6"
```

If you need  `scala.js http` log publishing integration use `zio-logging-slf4j` instead 

```scala
libraryDependencies += "dev.zio" %% "zio-logging-jshttp" % "0.2.6"
```


## Logger Interface

```scala
trait Logger[-A] { self =>

  /**
   * Produces a new logger by adapting a different input type to the input
   * type of this logger.
   */
  final def contramap[A1](f: A1 => A): Logger[A1] =
    new Logger[A1] {
      def locally[R1, E, A2](f: LogContext => LogContext)(zio: ZIO[R1, E, A2]): ZIO[R1, E, A2] =
        self.locally(f)(zio)

      def log(line: => A1): UIO[Unit] = self.log(f(line))

      def logContext: UIO[LogContext] = self.logContext
    }

  /**
   * Logs the specified element at the debug level.
   */
  def debug(line: => A): UIO[Unit] =
    self.log(LogLevel.Debug)(line)

  /**
   * Logs the specified element at the error level.
   */
  def error(line: => A): UIO[Unit] =
    self.log(LogLevel.Error)(line)

  /**
   * Logs the specified element at the error level with cause.
   */
  def error(line: => A, cause: Cause[Any]) =
    self.locally(LogAnnotation.Cause(Some(cause))) {
      self.log(LogLevel.Error)(line)
    }

  /**
   * Derives a new logger from this one, by applying the specified decorator
   * to the logger context.
   */
  def derive(f: LogContext => LogContext): Logger[A] =
    new Logger[A] {
      def locally[R1, E, A1](f: LogContext => LogContext)(zio: ZIO[R1, E, A1]): ZIO[R1, E, A1] =
        self.locally(f)(zio)

      def log(line: => A): UIO[Unit] = locally(f)(self.log(line))

      def logContext: UIO[LogContext] = self.logContext
    }

  /**
   * Logs the specified element at the info level
   */
  def info(line: => A): UIO[Unit] =
    self.log(LogLevel.Info)(line)

  /**
   * Modifies the log context in the scope of the specified effect.
   */
  def locally[R1, E, A1](f: LogContext => LogContext)(zio: ZIO[R1, E, A1]): ZIO[R1, E, A1]

  /**
   * Modifies the log context with effect in the scope of the specified effect.
   */
  def locallyM[R1, E, A1](f: LogContext => URIO[R1, LogContext])(zio: ZIO[R1, E, A1]): ZIO[R1, E, A1] =
    logContext.flatMap(ctx => f(ctx)).flatMap(ctx => locally(_ => ctx)(zio))

  /**
   * Modifies the annotate in the scope of the specified effect.
   */
  final def locallyAnnotate[B, R, E, A1](annotation: LogAnnotation[B], value: B)(zio: ZIO[R, E, A1]): ZIO[R, E, A1] =
    locally(_.annotate(annotation, value))(zio)

  /**
   * Logs the specified element using an inherited log level.
   */
  def log(line: => A): UIO[Unit]

  /**
   * Retrieves the log context.
   */
  def logContext: UIO[LogContext]

  /**
   * Logs the specified element at the specified level. Implementations may
   * override this for greater efficiency.
   */
  def log(level: LogLevel)(line: => A): UIO[Unit] =
    locally(_.annotate(LogAnnotation.Level, level))(log(line))

  /**
   * Produces a named logger.
   */
  def named(name: String): Logger[A] =
    derive(_.annotate(LogAnnotation.Name, name :: Nil))

  /**
   * Logs the specified element at the error level with exception.
   */
  def throwable(line: => A, t: Throwable) =
    self.locally(LogAnnotation.Throwable(Some(t))) {
      self.error(line)
    }

  /**
   * Logs the specified element at the trace level.
   */
  def trace(line: => A): UIO[Unit] =
    self.log(LogLevel.Trace)(line)

  /**
   * Logs the specified element at the warn level.
   */
  def warn(line: => A) =
    self.log(LogLevel.Warn)(line)
}
```

Library provides object `log` that exposes basic methods:

```scala
object log {
  def apply(line: => String): ZIO[Logging, Nothing, Unit] =
    ZIO.accessM[Logging](_.get.log(line))

  def apply(level: LogLevel)(line: => String): ZIO[Logging, Nothing, Unit] =
    ZIO.accessM[Logging](_.get.log(level)(line))

  val context: URIO[Logging, LogContext] =
    Logging.context

  def debug(line: => String): ZIO[Logging, Nothing, Unit] =
    Logging.debug(line)

  def error(line: => String): ZIO[Logging, Nothing, Unit] =
    Logging.error(line)

  def error(line: => String, cause: Cause[Any]): ZIO[Logging, Nothing, Unit] =
    Logging.error(line, cause)

  def info(line: => String): ZIO[Logging, Nothing, Unit] =
    Logging.info(line)

  def locally[A, R <: Logging, E, A1](fn: LogContext => LogContext)(zio: ZIO[R, E, A1]): ZIO[Logging with R, E, A1] =
    Logging.locally(fn)(zio)

  def locallyM[A, R <: Logging, E, A1](
    fn: LogContext => URIO[R, LogContext]
  )(zio: ZIO[R, E, A1]): ZIO[Logging with R, E, A1] =
    Logging.locallyM(fn)(zio)

  def throwable(line: => String, t: Throwable): ZIO[Logging, Nothing, Unit] =
    Logging.throwable(line, t)

  def trace(line: => String): ZIO[Logging, Nothing, Unit] =
    Logging.trace(line)

  def warn(line: => String): ZIO[Logging, Nothing, Unit] =
    Logging.warn(line)

}

```

### Logger Context
Logger Context is mechanism that we use to carry information like logger name or correlation id across different Fibers. Implementation uses `FiberRef` from `ZIO`. 

```scala
import zio.logging._
log.locally(LogAnnatation.Name("my-logger" :: Nil)) {
  log.info("log entry") // value of LogAnnotation.Name is only visible in this block
}
```

User of library is allowed to add custom `LogAnnatation`  

```scala
val customLogAnnotation = LogAnnatation("custom_annotation", 1, _ + _, _.toString)
```

## Examples

### Simple console log

```scala
import zio.logging._

object Simple extends zio.App {

  val env =
    Logging.console(
      format = (_, logEntry) => logEntry,
      rootLoggerName = Some("default-logger")
    )

  override def run(args: List[String]) =
    log("Hello from ZIO logger").provideCustomLayer(env).as(0)
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
    Logging.console((_, logEntry) =>
      logEntry
    )

  override def run(args: List[String]) =
    log.locally(LogAnnotation.Name("logger-name-here" :: Nil)) {
      log.debug("Hello from ZIO logger")
    }.provideCustomLayer(env).as(0)
}
```

Expected console output:

```
2020-02-02T18:22:33.200-05:00 DEBUG logger-name-here Hello from ZIO logger
```

### Slf4j and correlation id
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
      } yield 1).provideLayer(env)
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

### Scala.JS Console

Scala.Js console works as the JVM Console using the standard JS console for output.

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

### Scala.JS HTTP Ajax pusher

This scala.js implementation allows to send logs to a remote server. It's very useful to control the navigation and action inside a SPA.

All events are sent to a backend via Ajax POST.

The Json format of the event is the following

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

**clientId** is an identifier of the connection. Default is a UUID random generated

To create a logger, the **url** for the POST is mandatory. 


```scala
import zio.logging._
import zio.logging.js._
...

val loggerLayer=HTTPLogger.make("http://localhost:9000/event/collect")((context, message) => message)


```


