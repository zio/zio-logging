---
id: slf4j2
title: "SLF4J v2"
---

The Simple Logging Facade for Java ([`SLF4J v2`](https://www.slf4j.org/) - using JDK9+ module system [JPMS](http://openjdk.java.net/projects/jigsaw/spec/)) serves as a simple facade or abstraction for various logging frameworks (e.g. java.util.logging, logback, log4j).

In order to use this logging backend, we need to add the following line in our build.sbt file:

```scala
libraryDependencies += "dev.zio" %% "zio-logging-slf4j2" % "@VERSION@"
```

Logger layer:

```scala
import zio.logging.backend.SLF4J

val logger = Runtime.removeDefaultLoggers >>> SLF4J.slf4j
```

Default `SLF4J` logger setup:
* logger name (by default)  is extracted from `zio.Trace`
    * for example, trace `zio.logging.example.Slf4jSimpleApp.run(Slf4jSimpleApp.scala:17)` will have `zio.logging.example.Slf4jSimpleApp` as logger name
    * NOTE: custom logger name may be set by `zio.logging.loggerName` aspect
* all annotations (logger name and log marker name annotations are excluded) are passed like [key-value pairs](https://www.slf4j.org/manual.html#fluent)
* cause is logged as throwable

See also [LogFormat and LogAppender](formatting-log-records.md#logformat-and-logappender)

Custom logger name set by aspect:

```scala
ZIO.logInfo("Starting user operation") @@ zio.logging.loggerName("zio.logging.example.UserOperation")
```

Log marker name set by aspect:

```scala
ZIO.logInfo("Confidential user operation") @@ SLF4J.logMarkerName("CONFIDENTIAL")
```


## Examples

You can find the source code [here](https://github.com/zio/zio-logging/tree/master/examples/src/main/scala/zio/logging/example)


### SLF4J logger name and annotations

[//]: # (TODO: make snippet type-checked using mdoc)

```scala
package zio.logging.example

import zio.logging.LogAnnotation
import zio.logging.backend.SLF4J
import zio.{ ExitCode, Runtime, Scope, ZIO, ZIOAppDefault, _ }

import java.util.UUID

object Slf4jSimpleApp extends ZIOAppDefault {

  override val bootstrap: ZLayer[ZIOAppArgs, Any, Any] = Runtime.removeDefaultLoggers >>> SLF4J.slf4j

  private val users = List.fill(2)(UUID.randomUUID())

  override def run: ZIO[Scope, Any, ExitCode] =
    for {
      _       <- ZIO.logInfo("Start")
      traceId <- ZIO.succeed(UUID.randomUUID())
      _       <- ZIO.foreachPar(users) { uId =>
        {
          ZIO.logInfo("Starting user operation") *>
            ZIO.logInfo("Confidential user operation") @@ SLF4J.logMarkerName("CONFIDENTIAL") *>
            ZIO.sleep(500.millis) *>
            ZIO.logInfo("Stopping user operation")
        } @@ ZIOAspect.annotated("user", uId.toString)
      } @@ LogAnnotation.TraceId(traceId) @@ zio.logging.loggerName("zio.logging.example.UserOperation")
      _       <- ZIO.logInfo("Done")
    } yield ExitCode.success

}
```

Logback configuration:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<configuration>
    <appender name="STDOUT" class="ch.qos.logback.core.ConsoleAppender">
        <layout class="ch.qos.logback.classic.PatternLayout">
            <Pattern>%d{HH:mm:ss.SSS} [%thread] [%kvp] %-5level %logger{36} %msg%n</Pattern>
        </layout>
    </appender>
    <turboFilter class="ch.qos.logback.classic.turbo.MarkerFilter">
        <Name>CONFIDENTIAL_FILTER</Name>
        <Marker>CONFIDENTIAL</Marker>
        <OnMatch>DENY</OnMatch>
    </turboFilter>
    <root level="debug">
        <appender-ref ref="STDOUT" />
    </root>
</configuration>
```

Expected Console Output:
```
12:13:17.495 [ZScheduler-Worker-8] [] INFO  zio.logging.example.Slf4j2SimpleApp Start
12:13:17.601 [ZScheduler-Worker-11] [trace_id="7826dd28-7e37-4b54-b84d-05399bb6cbeb" user="7b6a1af5-bad2-4846-aa49-138d31b40a24"] INFO  zio.logging.example.UserOperation Starting user operation
12:13:17.601 [ZScheduler-Worker-10] [trace_id="7826dd28-7e37-4b54-b84d-05399bb6cbeb" user="4df9cdbc-e770-4bc9-b884-756e671a6435"] INFO  zio.logging.example.UserOperation Starting user operation
12:13:18.167 [ZScheduler-Worker-13] [trace_id="7826dd28-7e37-4b54-b84d-05399bb6cbeb" user="7b6a1af5-bad2-4846-aa49-138d31b40a24"] INFO  zio.logging.example.UserOperation Stopping user operation
12:13:18.167 [ZScheduler-Worker-15] [trace_id="7826dd28-7e37-4b54-b84d-05399bb6cbeb" user="4df9cdbc-e770-4bc9-b884-756e671a6435"] INFO  zio.logging.example.UserOperation Stopping user operation
12:13:18.173 [ZScheduler-Worker-13] [] INFO  zio.logging.example.Slf4j2SimpleApp Done
```

### Custom tracing annotation

Following application has custom aspect `currentTracingSpanAspect` implementation which taking current span from `Tracing` service 
which then is added to logs by log annotation.

```scala
package zio.logging.example

import zio.logging.backend.SLF4J
import zio.{ ExitCode, Runtime, Scope, ZIO, ZIOAppDefault, _ }

import java.util.UUID

trait Tracing {
  def getCurrentSpan(): UIO[String]
}

final class LiveTracing extends Tracing {
  override def getCurrentSpan(): UIO[String] = ZIO.succeed(UUID.randomUUID().toString)
}

object LiveTracing {
  val layer: ULayer[Tracing] = ZLayer.succeed(new LiveTracing)
}

object CustomTracingAnnotationApp extends ZIOAppDefault {

  private def currentTracingSpanAspect(key: String): ZIOAspect[Nothing, Tracing, Nothing, Any, Nothing, Any] =
    new ZIOAspect[Nothing, Tracing, Nothing, Any, Nothing, Any] {
      def apply[R <: Tracing, E, A](zio: ZIO[R, E, A])(implicit trace: Trace): ZIO[R, E, A] =
        ZIO.serviceWithZIO[Tracing] { tracing =>
          tracing.getCurrentSpan().flatMap { span =>
            ZIO.logAnnotate(key, span)(zio)
          }
        }
    }

  override val bootstrap: ZLayer[ZIOAppArgs, Any, Any] = Runtime.removeDefaultLoggers >>> SLF4J.slf4j

  private val users = List.fill(2)(UUID.randomUUID())

  override def run: ZIO[Scope, Any, ExitCode] =
    (for {
      _ <- ZIO.foreachPar(users) { uId =>
        {
          ZIO.logInfo("Starting operation") *>
            ZIO.sleep(500.millis) *>
            ZIO.logInfo("Stopping operation")
        } @@ ZIOAspect.annotated("user", uId.toString)
      } @@ currentTracingSpanAspect("trace_id")
      _ <- ZIO.logInfo("Done")
    } yield ExitCode.success).provide(LiveTracing.layer)

}
```

Expected Console Output:
```
19:09:57.695 [ZScheduler-Worker-9] trace_id= user_id= INFO  z.l.e.CustomTracingAnnotationApp Starting operation
19:09:57.695 [ZScheduler-Worker-9] trace_id=403fe6e9-f666-4688-a609-04813ac26892 user_id=35d36d10-4b64-48fc-bf9d-6b6b37d2f4cc INFO  z.l.e.CustomTracingAnnotationApp Starting operation
19:09:58.056 [ZScheduler-Worker-8] trace_id=403fe6e9-f666-4688-a609-04813ac26892 user_id=068a35f2-2633-4404-9522-ffbfabe63730 INFO  z.l.e.CustomTracingAnnotationApp Stopping operation
19:09:58.197 [ZScheduler-Worker-10] trace_id=403fe6e9-f666-4688-a609-04813ac26892 user_id=35d36d10-4b64-48fc-bf9d-6b6b37d2f4cc INFO  z.l.e.CustomTracingAnnotationApp Stopping operation
19:09:58.202 [ZScheduler-Worker-13] trace_id= user_id= INFO  z.l.e.CustomTracingAnnotationApp Done
```
