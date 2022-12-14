---
id: json-console-logger
title: "JSON Console Logger"
---

[//]: # (TODO: make snippet type-checked using mdoc)

```scala
package zio.logging.example

import zio.logging.{ LogAnnotation, LogFormat, consoleJson }
import zio.{ ExitCode, Runtime, Scope, ZIO, ZIOAppDefault, _ }

import java.util.UUID

object ConsoleJsonApp extends ZIOAppDefault {

  private val userLogAnnotation = LogAnnotation[UUID]("user", (_, i) => i, _.toString)

  override val bootstrap: ZLayer[ZIOAppArgs, Any, Any] =
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
    } yield ExitCode.success)

}
```

Expected console output:

```
{"timestamp":"2022-10-28T13:48:20.350244+02:00","level":"INFO","thread":"zio-fiber-8","message":"Starting operation","trace_id":"674a118e-2944-46a7-8db2-ceb79d91d51d","user":"b4cf9c71-5b1d-4fe1-bfb4-35a6e51483b2"}
{"timestamp":"2022-10-28T13:48:20.350238+02:00","level":"INFO","thread":"zio-fiber-7","message":"Starting operation","trace_id":"674a118e-2944-46a7-8db2-ceb79d91d51d","user":"372071a6-a643-452b-a07c-d0966e556bfa"}
{"timestamp":"2022-10-28T13:48:20.899453+02:00","level":"INFO","thread":"zio-fiber-7","message":"Stopping operation","trace_id":"674a118e-2944-46a7-8db2-ceb79d91d51d","user":"372071a6-a643-452b-a07c-d0966e556bfa"}
{"timestamp":"2022-10-28T13:48:20.899453+02:00","level":"INFO","thread":"zio-fiber-8","message":"Stopping operation","trace_id":"674a118e-2944-46a7-8db2-ceb79d91d51d","user":"b4cf9c71-5b1d-4fe1-bfb4-35a6e51483b2"}
{"timestamp":"2022-10-28T13:48:20.908254+02:00","level":"INFO","thread":"zio-fiber-6","message":"Done"}
```
