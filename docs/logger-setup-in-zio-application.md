---
id: logger-setup-in-zio-application
title: "Logger setup in ZIO application"
---

The recommended place for setting the logger is application boostrap.
In this case, custom logger will be set for whole application runtime (also application failures will be logged with specified logger).

[//]: # (TODO: make snippet type-checked using mdoc)

```scala
import zio.logging.{ LogFormat, console }
import zio.{ ExitCode, Runtime, Scope, ZIO, ZIOAppArgs, ZIOAppDefault, ZLayer }

object SimpleApp extends ZIOAppDefault {

  override val bootstrap: ZLayer[ZIOAppArgs, Any, Any] =
    Runtime.removeDefaultLoggers >>> console(LogFormat.default)

  override def run: ZIO[Scope, Any, ExitCode] =
    for {
      _ <- ZIO.logInfo("Start")
      _ <- ZIO.fail("FAILURE")
      _ <- ZIO.logInfo("Done")
    } yield ExitCode.success

}
```

Expected console output:

```
timestamp=2022-10-28T18:40:25.517623+02:00 level=INFO thread=zio-fiber-6 message="Start"
timestamp=2022-10-28T18:40:25.54676+02:00  level=ERROR thread=zio-fiber-0 message="" cause=Exception in thread "zio-fiber-6" java.lang.String: FAILURE
	at zio.logging.example.SimpleApp.run(SimpleApp.scala:14)
```
