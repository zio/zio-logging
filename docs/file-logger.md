---
id: file-logger
title: "File Logger"
---

## Configuration

FileLoggerConfig


## Examples

You can find the source code [here](https://github.com/zio/zio-logging/tree/master/examples)

### JSON Console Logger 

[//]: # (TODO: make snippet type-checked using mdoc)

```scala
package zio.logging.example

import zio.logging.fileLogger
import zio.{ Config, ConfigProvider, ExitCode, LogLevel, Runtime, Scope, ZIO, ZIOAppDefault, ZLayer }

object FileApp extends ZIOAppDefault {

  val logPattern =
    "%timestamp{yyyy-MM-dd'T'HH:mm:ssZ} %fixed{7}{%level} [%fiberId] %name:%line %message %cause"

  val configProvider: ConfigProvider = ConfigProvider.fromMap(
    Map(
      "logger/pattern"          -> logPattern,
      "logger/path"             -> "file:///tmp/file_app.log",
      "logger/filter/rootLevel" -> LogLevel.Info.label
    ),
    "/"
  )

  override val bootstrap: ZLayer[Any, Config.Error, Unit] =
    Runtime.removeDefaultLoggers >>> Runtime.setConfigProvider(configProvider) >>> fileLogger()

  override def run: ZIO[Scope, Any, ExitCode] =
    for {
      _ <- ZIO.logInfo("Start")
      _ <- ZIO.fail("FAILURE")
      _ <- ZIO.logInfo("Done")
    } yield ExitCode.success
}

```

Expected file content:

```
2023-03-05T12:37:31+0100 INFO    [zio-fiber-4] zio.logging.example.FileApp:40 Start
2023-03-05T12:37:31+0100 ERROR   [zio-fiber-0] zio-logger:  Exception in thread "zio-fiber-4" java.lang.String: FAILURE
        at zio.logging.example.FileApp.run(FileApp.scala:41)
```
