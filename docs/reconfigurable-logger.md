---
id: reconfigurable-logger
title: "Reconfigurable Logger"
---

`ReconfigurableLogger` is adding support for updating logger configuration in application runtime. 

logger layer with configuration from config provider (example with [Console Logger)](console-logger.md)):


```scala
import zio.logging.{ consoleLogger, ConsoleLoggerConfig, ReconfigurableLogger }
import zio._

val configProvider: ConfigProvider = ???

val logger = Runtime.removeDefaultLoggers >>> Runtime.setConfigProvider(configProvider) >>> ReconfigurableLogger
        .make[Any, Nothing, String, Any, ConsoleLoggerConfig](
          ConsoleLoggerConfig.load().orDie, // loading current configuration
          (config, _) => makeConsoleLogger(config), // make logger from loaded configuration
          Schedule.fixed(5.second) // default is 10 seconds 
        )
        .installUnscoped[Any]
```

`ReconfigurableLogger`, based on given `Schedule` and load configuration function, will recreate logger if configuration changed.

## Examples

You can find the source code [here](https://github.com/zio/zio-logging/tree/master/examples)


### Colorful Console Logger With Reconfiguration In Runtime

[//]: # (TODO: make snippet type-checked using mdoc)

By default, root level configuration is `INFO`, when root level configuration is changed to `DEBUG`, other logger messages should be in output.

Configuration:

```
logger {
  format = "%highlight{%timestamp{yyyy-MM-dd'T'HH:mm:ssZ} %fixed{7}{%level} [%fiberId] %name:%line %message %kv{trace_id} %kv{user_id} %cause}"
  filter {
    rootLevel = "DEBUG"
  }
}
```

Application:

```scala
package zio.logging.example

import com.typesafe.config.ConfigFactory
import zio.config.typesafe.TypesafeConfigProvider
import zio.logging.{ ConsoleLoggerConfig, LogAnnotation, ReconfigurableLogger, _ }
import zio.{ Config, ExitCode, Runtime, Scope, ZIO, ZIOAppDefault, _ }

import java.util.UUID

object LoggerReconfigureApp extends ZIOAppDefault {

  def configuredLogger(
            loadConfig: => ZIO[Any, Config.Error, ConsoleLoggerConfig]
    ): ZLayer[Any, Config.Error, Unit] =
    ReconfigurableLogger
            .make[Any, Config.Error, String, Any, ConsoleLoggerConfig](
              loadConfig,
              (config, _) => makeConsoleLogger(config),
              Schedule.fixed(500.millis)
            )
            .installUnscoped

  override val bootstrap: ZLayer[ZIOAppArgs, Any, Any] =
    Runtime.removeDefaultLoggers >>> configuredLogger(
      for {
        config       <- ZIO.succeed(ConfigFactory.load("logger.conf"))
        _            <- Console.printLine(config.getConfig("logger")).orDie
        loggerConfig <-
                TypesafeConfigProvider.fromTypesafeConfig(config).nested("logger").load(ConsoleLoggerConfig.config)
      } yield loggerConfig
    )

  def exec(): ZIO[Any, Nothing, Unit] =
    for {
      ok      <- Random.nextBoolean
      traceId <- ZIO.succeed(UUID.randomUUID())
      _       <- ZIO.logDebug("Start") @@ LogAnnotation.TraceId(traceId)
      userIds <- ZIO.succeed(List.fill(2)(UUID.randomUUID().toString))
      _       <- ZIO.foreachPar(userIds) { userId =>
        {
          ZIO.logDebug("Starting operation") *>
                  ZIO.logInfo("OK operation").when(ok) *>
                  ZIO.logError("Error operation").when(!ok) *>
                  ZIO.logDebug("Stopping operation")
        } @@ LogAnnotation.UserId(userId)
      } @@ LogAnnotation.TraceId(traceId)
      _       <- ZIO.logDebug("Done") @@ LogAnnotation.TraceId(traceId)
    } yield ()

  override def run: ZIO[Scope, Any, ExitCode] =
    for {
      _ <- exec().repeat(Schedule.fixed(500.millis))
    } yield ExitCode.success

}
```

Expected console output:

```
Config(SimpleConfigObject({"filter":{"mappings":{},"rootLevel":"INFO"},"format":"%highlight{%timestamp{yyyy-MM-dd'T'HH:mm:ssZ} %fixed{7}{%level} [%fiberId] %name:%line %message %kv{trace_id} %kv{user_id} %cause}"}))
2023-12-25T11:11:27+0100 ERROR   [zio-fiber-106] zio.logging.example.LoggerReconfigureApp:58 Error operation trace_id=18788cfd-5efe-488b-8549-c69f374110ea user_id=b31bb7a0-f8d1-4d7d-8215-1c980ec0e986 
2023-12-25T11:11:27+0100 ERROR   [zio-fiber-107] zio.logging.example.LoggerReconfigureApp:58 Error operation trace_id=18788cfd-5efe-488b-8549-c69f374110ea user_id=2c390abc-17a7-413d-9d4a-668215727fbd 
Config(SimpleConfigObject({"filter":{"mappings":{},"rootLevel":"INFO"},"format":"%highlight{%timestamp{yyyy-MM-dd'T'HH:mm:ssZ} %fixed{7}{%level} [%fiberId] %name:%line %message %kv{trace_id} %kv{user_id} %cause}"}))
2023-12-25T11:11:28+0100 ERROR   [zio-fiber-108] zio.logging.example.LoggerReconfigureApp:58 Error operation trace_id=865905f4-9548-44b6-bd60-9b2d30da95f4 user_id=6abecac0-b867-4f50-a699-c344d2548b79 
2023-12-25T11:11:28+0100 ERROR   [zio-fiber-109] zio.logging.example.LoggerReconfigureApp:58 Error operation trace_id=865905f4-9548-44b6-bd60-9b2d30da95f4 user_id=b7f73c21-2dc6-4707-8bdd-96cfd7500e26 
Config(SimpleConfigObject({"filter":{"mappings":{},"rootLevel":"DEBUG"},"format":"%highlight{%timestamp{yyyy-MM-dd'T'HH:mm:ssZ} %fixed{7}{%level} [%fiberId] %name:%line %message %kv{trace_id} %kv{user_id} %cause}"}))
2023-12-25T11:11:28+0100 DEBUG   [zio-fiber-5] zio.logging.example.LoggerReconfigureApp:52 Start trace_id=44ac195b-9e17-42d8-b8ff-264a52275855  
2023-12-25T11:11:28+0100 DEBUG   [zio-fiber-110] zio.logging.example.LoggerReconfigureApp:56 Starting operation trace_id=44ac195b-9e17-42d8-b8ff-264a52275855 user_id=a976cb5b-d035-488f-9664-3761a07fb2d9 
2023-12-25T11:11:28+0100 DEBUG   [zio-fiber-111] zio.logging.example.LoggerReconfigureApp:56 Starting operation trace_id=44ac195b-9e17-42d8-b8ff-264a52275855 user_id=ed23b03a-00e5-4495-8a0d-092eee0a7479 
2023-12-25T11:11:28+0100 INFO    [zio-fiber-110] zio.logging.example.LoggerReconfigureApp:57 OK operation trace_id=44ac195b-9e17-42d8-b8ff-264a52275855 user_id=a976cb5b-d035-488f-9664-3761a07fb2d9 
2023-12-25T11:11:28+0100 INFO    [zio-fiber-111] zio.logging.example.LoggerReconfigureApp:57 OK operation trace_id=44ac195b-9e17-42d8-b8ff-264a52275855 user_id=ed23b03a-00e5-4495-8a0d-092eee0a7479 
2023-12-25T11:11:28+0100 DEBUG   [zio-fiber-111] zio.logging.example.LoggerReconfigureApp:59 Stopping operation trace_id=44ac195b-9e17-42d8-b8ff-264a52275855 user_id=ed23b03a-00e5-4495-8a0d-092eee0a7479 
2023-12-25T11:11:28+0100 DEBUG   [zio-fiber-110] zio.logging.example.LoggerReconfigureApp:59 Stopping operation trace_id=44ac195b-9e17-42d8-b8ff-264a52275855 user_id=a976cb5b-d035-488f-9664-3761a07fb2d9 
2023-12-25T11:11:28+0100 DEBUG   [zio-fiber-5] zio.logging.example.LoggerReconfigureApp:62 Done trace_id=44ac195b-9e17-42d8-b8ff-264a52275855  
Config(SimpleConfigObject({"filter":{"mappings":{},"rootLevel":"DEBUG"},"format":"%highlight{%timestamp{yyyy-MM-dd'T'HH:mm:ssZ} %fixed{7}{%level} [%fiberId] %name:%line %message %kv{trace_id} %kv{user_id} %cause}"}))
2023-12-25T11:11:29+0100 DEBUG   [zio-fiber-5] zio.logging.example.LoggerReconfigureApp:52 Start trace_id=f613ff34-8d8a-49e8-b58c-5fa954f3bc79  
2023-12-25T11:11:29+0100 DEBUG   [zio-fiber-113] zio.logging.example.LoggerReconfigureApp:56 Starting operation trace_id=f613ff34-8d8a-49e8-b58c-5fa954f3bc79 user_id=0c513b80-3fc6-4aa9-a805-11e658805296 
2023-12-25T11:11:29+0100 DEBUG   [zio-fiber-112] zio.logging.example.LoggerReconfigureApp:56 Starting operation trace_id=f613ff34-8d8a-49e8-b58c-5fa954f3bc79 user_id=aa296f97-6503-4633-ac78-16094f00ceca 
2023-12-25T11:11:29+0100 ERROR   [zio-fiber-113] zio.logging.example.LoggerReconfigureApp:58 Error operation trace_id=f613ff34-8d8a-49e8-b58c-5fa954f3bc79 user_id=0c513b80-3fc6-4aa9-a805-11e658805296 
2023-12-25T11:11:29+0100 ERROR   [zio-fiber-112] zio.logging.example.LoggerReconfigureApp:58 Error operation trace_id=f613ff34-8d8a-49e8-b58c-5fa954f3bc79 user_id=aa296f97-6503-4633-ac78-16094f00ceca 
2023-12-25T11:11:29+0100 DEBUG   [zio-fiber-113] zio.logging.example.LoggerReconfigureApp:59 Stopping operation trace_id=f613ff34-8d8a-49e8-b58c-5fa954f3bc79 user_id=0c513b80-3fc6-4aa9-a805-11e658805296 
2023-12-25T11:11:29+0100 DEBUG   [zio-fiber-112] zio.logging.example.LoggerReconfigureApp:59 Stopping operation trace_id=f613ff34-8d8a-49e8-b58c-5fa954f3bc79 user_id=aa296f97-6503-4633-ac78-16094f00ceca 
2023-12-25T11:11:29+0100 DEBUG   [zio-fiber-5] zio.logging.example.LoggerReconfigureApp:62 Done trace_id=f613ff34-8d8a-49e8-b58c-5fa954f3bc79  
```
