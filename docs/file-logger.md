---
id: file-logger
title: "File Logger"
---

logger layer with configuration from config provider:

```scala
import zio.logging.fileLogger
import zio.{ ConfigProvider, Runtime }

val configProvider: ConfigProvider = ???

val logger = Runtime.removeDefaultLoggers >>> Runtime.setConfigProvider(configProvider) >>> fileLogger()
```

logger layer with given configuration:

```scala
import zio.logging.{ fileLogger, FileLoggerConfig }
import zio.Runtime

val config: FileLoggerConfig = ???

val logger = Runtime.removeDefaultLoggers >>> fileLogger(config)
```

there are other version of file loggers:
* `zio.logging.fileJsonLogger` - output in json format
* file async:
    * `zio.logging.fileAsynLogger` - output in string format
    * `zio.logging.fileAsyncJsonLogger` - output in json format

## Configuration

the configuration for file logger (`zio.logging.FileLoggerConfig`) has the following structure:

```
logger {
  # log pattern
  pattern = "%timestamp{yyyy-MM-dd'T'HH:mm:ssZ} %level [%fiberId] %name:%line %message %cause"
  
  # URI to file
  path = "file:///tmp/console_app.log"
    
  # charset configuration, default value: UTF-8
  charset = "UTF-8"

  # auto flush batch size, default value: 1
  autoFlushBatchSize = 1

  # if defined, buffered writer is used, with given buffer size
  # bufferedIOSize = 8192
  
  filter {
    # see filter configuration
    rootLevel = INFO
  }
}
```

see also [log pattern](formatting-log-records.md#logpattern) and [filter configuration](log-filter.md#configuration)


## Examples

You can find the source code [here](https://github.com/zio/zio-logging/tree/master/examples)

### File Logger 

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
