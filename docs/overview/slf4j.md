---
id: overview_slf4j
title: "Slf4j"
---

## Slf4j

If you need [`slf4j`](https://www.slf4j.org/) integration use `zio-logging-slf4j`:

```scala
libraryDependencies += "dev.zio" %% "zio-logging-slf4j" % version
```

`slf4j` logger layer:

```scala
import zio.logging.backend.SLF4J

val logger = Runtime.removeDefaultLoggers >>> SLF4J.slf4j
```

Default `slf4j` logger setup:
* logger name (by default)  is extracted from `zio.Trace`
    * for example, trace `zio.logging.example.Slf4jAnnotationApp.run(Slf4jSimpleApp.scala:17)` will have `zio.logging.example.Slf4jSimpleApp` as logger name
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
            ZIO.logInfo("Confidential user operation") @@ SLF4J.logMarkerName("CONFIDENTIAL") *>
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
20:52:39.271 [ZScheduler-Worker-10] trace_id= user_id= INFO  zio.logging.example.Slf4jSimpleApp Start
20:52:39.307 [ZScheduler-Worker-9] trace_id=170754d9-51a6-4916-beff-0a26c97e01dd user_id=c9b688b6-5fa1-4ea8-a8f0-e0b20a1cf07f INFO  zio.logging.example.UserOperation Starting user operation
20:52:39.307 [ZScheduler-Worker-8] trace_id=170754d9-51a6-4916-beff-0a26c97e01dd user_id=74d16bcd-6f62-45fd-95d6-0319d1524fe8 INFO  zio.logging.example.UserOperation Starting user operation
20:52:39.840 [ZScheduler-Worker-13] trace_id=170754d9-51a6-4916-beff-0a26c97e01dd user_id=74d16bcd-6f62-45fd-95d6-0319d1524fe8 INFO  zio.logging.example.UserOperation Stopping user operation
20:52:39.840 [ZScheduler-Worker-2] trace_id=170754d9-51a6-4916-beff-0a26c97e01dd user_id=c9b688b6-5fa1-4ea8-a8f0-e0b20a1cf07f INFO  zio.logging.example.UserOperation Stopping user operation
20:52:39.846 [ZScheduler-Worker-3] trace_id= user_id= INFO  zio.logging.example.Slf4jSimpleApp Done
```
