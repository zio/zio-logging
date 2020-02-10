---
id: overview_index
title: "Summary"
---

ZIO Logging is a functional, type-safe library for logging.

## Installation

`ZIO-Logging` is available via maven repo so import in `build.sbt` is sufficient:

```scala
libraryDependencies += "dev.zio" %% "zio-logging" % "0.2.0"
```

If you need `slf4j` integration use `zio-logging-slf4j` instead 

```scala
libraryDependencies += "dev.zio" %% "zio-logging-slf4j" % "0.2.0"
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

Liabrary provides package object that exposes basic methods:

```scala
package object logging {
  def log(line: => String): ZIO[Logging, Nothing, Unit] 

  def log(level: LogLevel)(line: => String): ZIO[Logging, Nothing, Unit] 

  def locallyAnnotate[A, R <: Logging, E, A1](annotation: LogAnnotation[A], value: A)(
    zio: ZIO[R, E, A1]
  ): ZIO[Logging with R, E, A1] 
}
```

### Logger Context
Loggger Contex is mechanism that we use to carry informations like logger name or correlation id across different Fibers. Implementation uses `FiberRef` from `ZIO`. 

```scala
import zio.logging._
locallyAnnotate(LogAnnatation.Name, "my-logger" :: Nil) {
  log("log entry") // value of LogAnnotation.Name is only visible in this block
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
    log("Hello from ZIO logger").as(0).provideSomeM(env)
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
   locally(LogAnnotation.Name("logger-name-here" :: Nil)) { 
    log(LogLevel.Debug)("Hello from ZIO logger")
   }.as(0).provideSomeM(env)
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
      _     <- log("info message without correlation id")
      _ <- locallyAnnotate(LogAnnotation.CorrelationId, generateCorrelationId) {
            log("info message with correlation id") *>
              log(LogLevel.Error)("another info message with correlation id").fork
          }
    } yield 0).provideSomeM(env)
}

```


Expected Console Output:
```
18:47:32.392 [zio-default-async-1-78428773] INFO  Slf4jAndCorrelationId$ [correlation-id = undefined-correlation-id] info message without correlation id
18:47:32.434 [zio-default-async-1-78428773] INFO  Slf4jAndCorrelationId$ [correlation-id = 9da4b9be-9e27-4af6-b687-d96851ab15f1] info message with correlation id
18:47:32.440 [zio-default-async-2-78428773] ERROR Slf4jAndCorrelationId$ [correlation-id = 9da4b9be-9e27-4af6-b687-d96851ab15f1] another info message with correlation id
```



