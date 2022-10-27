---
id: overview_jpl
title: "Java Platform/System Logger`"
---

## Java Platform/System Logger

If you need [`Java Platform/System Logger`](https://openjdk.org/jeps/264) integration use `zio-logging-jpl`:

```scala
libraryDependencies += "dev.zio" %% "zio-logging-jpl" % version
```

`jpl` logger layer:

```scala
import zio.logging.backend.JPL

val logger = Runtime.removeDefaultLoggers >>> JPL.jpl
```

Default `jpl` logger setup:
* logger name (by default)  is extracted from `zio.Trace`
    * for example, trace `zio.logging.example.Slf4jAnnotationApp.run(Slf4jSimpleApp.scala:17)` will have `zio.logging.example.Slf4jSimpleApp` as logger name
    * NOTE: custom logger name may be set by `JPL.loggerName` aspect
* all annotations (logger name annotation is excluded) are placed at the beginning of log message
* cause is logged as throwable

Custom logger name set by aspect:

```scala
ZIO.logInfo("Starting user operation") @@ JPL.loggerName("zio.logging.example.UserOperation")
```

## Examples

You can find the source code [here](https://github.com/zio/zio-logging/tree/master/examples/src/main/scala/zio/logging/example)

### Java Platform/System logger name and annotations

```scala
package zio.logging.example

import zio.logging.LogAnnotation
import zio.logging.backend.JPL
import zio.{ExitCode, Runtime, Scope, ZIO, ZIOAppDefault, _}

import java.util.UUID

object JplSimpleApp extends ZIOAppDefault {

  private val logger = Runtime.removeDefaultLoggers >>> JPL.jpl

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
      } @@ LogAnnotation.TraceId(traceId) @@ JPL.loggerName("zio.logging.example.UserOperation")
      _       <- ZIO.logInfo("Done")
    } yield ExitCode.success).provide(logger)

}

```

Expected Console Output:
```
Aug 18, 2022 6:51:10 PM zio.logging.backend.JPL$$anon$2 $anonfun$closeLogEntry$1
INFO: Start
Aug 18, 2022 6:51:10 PM zio.logging.backend.JPL$$anon$2 $anonfun$closeLogEntry$1
INFO:  user=d0c3b1ac-d0f5-4879-b398-3dab5efdc9d4 trace_id=92e5e9fd-71b6-4491-a97d-101d367bc64e Starting user operation
Aug 18, 2022 6:51:10 PM zio.logging.backend.JPL$$anon$2 $anonfun$closeLogEntry$1
INFO:  user=f4327982-c838-4c03-8839-49ebc95f2b6b trace_id=92e5e9fd-71b6-4491-a97d-101d367bc64e Starting user operation
Aug 18, 2022 6:51:10 PM zio.logging.backend.JPL$$anon$2 $anonfun$closeLogEntry$1
INFO:  user=d0c3b1ac-d0f5-4879-b398-3dab5efdc9d4 trace_id=92e5e9fd-71b6-4491-a97d-101d367bc64e Stopping user operation
Aug 18, 2022 6:51:10 PM zio.logging.backend.JPL$$anon$2 $anonfun$closeLogEntry$1
INFO:  user=f4327982-c838-4c03-8839-49ebc95f2b6b trace_id=92e5e9fd-71b6-4491-a97d-101d367bc64e Stopping user operation
Aug 18, 2022 6:51:10 PM zio.logging.backend.JPL$$anon$2 $anonfun$closeLogEntry$1
INFO: Done
```