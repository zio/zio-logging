---
id: slf4j
title: "SLF4J"
---

The Simple Logging Facade for Java ([`SLF4J`](https://www.slf4j.org/)) serves as a simple facade or abstraction for various logging frameworks (e.g. java.util.logging, logback, log4j).

In order to use this logging backend, we need to add the following line in our build.sbt file:

```scala
libraryDependencies += "dev.zio" %% "zio-logging-slf4j" % @VERSION@
```

Logger layer:

```scala
import zio.logging.backend.SLF4J

val logger = Runtime.removeDefaultLoggers >>> SLF4J.slf4j
```

Default `SLF4J` logger setup:
* logger name (by default)  is extracted from `zio.Trace`
    * for example, trace `zio.logging.example.Slf4jSimpleApp.run(Slf4jSimpleApp.scala:17)` will have `zio.logging.example.Slf4jSimpleApp` as logger name
    * NOTE: custom logger name may be set by `SLF4J.loggerName` aspect
* all annotations (logger name and log marker name annotations are excluded) are placed into MDC context
* cause is logged as throwable

Custom logger name set by aspect:

```scala
ZIO.logInfo("Starting user operation") @@ SLF4J.loggerName("zio.logging.example.UserOperation")
```

Log marker name set by aspect:

```scala
ZIO.logInfo("Confidential user operation") @@ SLF4J.logMarkerName("CONFIDENTIAL")
```


## Examples

You can find the source code [here](https://github.com/zio/zio-logging/tree/master/examples/src/main/scala/zio/logging/example)


### SLF4J logger name and annotations

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
      } @@ LogAnnotation.TraceId(traceId) @@ SLF4J.loggerName("zio.logging.example.UserOperation")
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
            <Pattern>%d{HH:mm:ss.SSS} [%thread] trace_id=%X{trace_id} user_id=%X{user} %-5level %logger{36} %msg%n</Pattern>
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
13:49:14.060 [ZScheduler-Worker-2] trace_id= user_id= INFO  zio.logging.example.Slf4jSimpleApp Start
13:49:14.090 [ZScheduler-Worker-12] trace_id=98cdf7b7-dc09-4935-8cbc-4a3399b67d2a user_id=3b6163f5-0677-4909-b17f-c181b53312b6 INFO  zio.logging.example.UserOperation Starting user operation
13:49:14.091 [ZScheduler-Worker-8] trace_id=98cdf7b7-dc09-4935-8cbc-4a3399b67d2a user_id=75e17c12-d397-455c-89b1-4e5292d860ba INFO  zio.logging.example.UserOperation Starting user operation
13:49:14.616 [ZScheduler-Worker-0] trace_id=98cdf7b7-dc09-4935-8cbc-4a3399b67d2a user_id=3b6163f5-0677-4909-b17f-c181b53312b6 INFO  zio.logging.example.UserOperation Stopping user operation
13:49:14.616 [ZScheduler-Worker-10] trace_id=98cdf7b7-dc09-4935-8cbc-4a3399b67d2a user_id=75e17c12-d397-455c-89b1-4e5292d860ba INFO  zio.logging.example.UserOperation Stopping user operation
13:49:14.626 [ZScheduler-Worker-0] trace_id= user_id= INFO  zio.logging.example.Slf4jSimpleApp Done
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
