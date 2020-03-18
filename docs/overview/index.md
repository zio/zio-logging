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



## Logger Interface

```scala
trait LoggerLike[-A] { self =>

  /**
   * Produces a new logger by adapting a different input type to the input
   * type of this logger.
   */
  def contramap[A1](f: A1 => A): LoggerLike[A1]
  
  /**
   * Derives a new logger from this one, by applying the specified decorator
   * to the logger context.
   */
  def derive(f: LogContext => LogContext): LoggerLike[A]

  /**
   * Modifies the log context in the scope of the specified effect.
   */
  def locally[R1, E, A1](f: LogContext => LogContext)(zio: ZIO[R1, E, A1]): ZIO[R1, E, A1]

  /**
   * Modifies the log context with effect in the scope of the specified effect.
   */
  def locallyM[R1, E, A1](f: LogContext => URIO[R1, LogContext])(zio: ZIO[R1, E, A1]): ZIO[R1, E, A1] 

  /**
   * Modifies the annotate in the scope of the specified effect.
   */
  final def locallyAnnotate[B, R, E, A1](annotation: LogAnnotation[B], value: B)(zio: ZIO[R, E, A1]): ZIO[R, E, A1] 

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
  def log(level: LogLevel)(line: => A): UIO[Unit] 

  /**
   * Produces a named logger.
   */
  def named(name: String): LoggerLike[A] 
}
```

Library provides object `log` that exposes basic methods:

```scala
object log {
  def apply(line: => String): ZIO[Logging, Nothing, Unit] =
    ZIO.accessM[Logging](_.get.logger.log(line))

  def apply(level: LogLevel)(line: => String): ZIO[Logging, Nothing, Unit] =
    ZIO.accessM[Logging](_.get.logger.log(level)(line))

  def context: URIO[Logging, LogContext] =
    ZIO.accessM[Logging](_.get.logger.logContext)

  def debug(line: => String): ZIO[Logging, Nothing, Unit] =
    log(LogLevel.Debug)(line)

  def error(line: => String): ZIO[Logging, Nothing, Unit] =
    log(LogLevel.Error)(line)

  def error(line: => String, cause: Cause[Any]): ZIO[Logging, Nothing, Unit] =
    log(LogLevel.Error)(line + System.lineSeparator() + cause.prettyPrint)

  def info(line: => String): ZIO[Logging, Nothing, Unit] =
    log(LogLevel.Info)(line)

  def locally[A, R <: Logging, E, A1](fn: LogContext => LogContext)(zio: ZIO[R, E, A1]): ZIO[Logging with R, E, A1] =
    ZIO.accessM(_.get.logger.locally(fn)(zio))

  def locallyM[A, R <: Logging, E, A1](
    fn: LogContext => URIO[R, LogContext]
  )(zio: ZIO[R, E, A1]): ZIO[Logging with R, E, A1] =
    ZIO.accessM(_.get.logger.locallyM(fn)(zio))

  def logger: URIO[Logging, Logger] =
    ZIO.access[Logging](_.get.logger)

  def throwable(line: => String, t: Throwable): ZIO[Logging, Nothing, Unit] =
    error(line, Cause.die(t))

  def trace(line: => String): ZIO[Logging, Nothing, Unit] =
    log(LogLevel.Trace)(line)

  def warn(line: => String): ZIO[Logging, Nothing, Unit] =
    log(LogLevel.Warn)(line)

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
    Logging.console((_, logEntry) =>
      logEntry
    )

  override def run(args: List[String]) =
    env >>> log("Hello from ZIO logger").as(0)
}

```

Expected console output:

```
2020-02-02T18:09:45.197-05:00 INFO  Hello from ZIO logger
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
   env >>> log.locally(LogAnnotation.Name("logger-name-here" :: Nil)) { 
    log.debug("Hello from ZIO logger")
   }.as(0)
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



