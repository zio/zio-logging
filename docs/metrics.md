---
id: metrics
title: "Log Metrics"
---

Log metrics collecting metrics related to ZIO logging (all `ZIO.log*` functions). 
As ZIO core supporting multiple loggers, this logging metrics collector is implemented as specific `ZLogger` 
which is responsible just for collecting metrics of all logs - `ZIO.log*` functions.

Metrics layer:

```scala
val layer = zio.logging.logMetrics
```

Metrics:
* zio_logger_all_total - logs count for `LogLevel.All`
* zio_logger_fatal_total - logs count for `LogLevel.Fatal`
* zio_logger_error_total - logs count for `LogLevel.Error`
* zio_logger_warn_total - logs count for `LogLevel.Warning`
* zio_logger_info_total - logs count for `LogLevel.Info`
* zio_logger_debug_total - logs count for `LogLevel.Debug`
* zio_logger_trace_total - logs count for `LogLevel.Trace`
* zio_logger_none_total - logs count for `LogLevel.None`


## Examples

You can find the source code [here](https://github.com/zio/zio-logging/tree/master/examples/src/main/scala/zio/logging/example)


### Console logger with metrics

[//]: # (TODO: make snippet type-checked using mdoc)

```scala
package zio.logging.example

import zio.logging.{ LogFormat, console, logMetrics }
import zio.{ ExitCode, Runtime, Scope, ZIO, ZIOAppArgs, ZIOAppDefault, ZLayer }
import zio.metrics.connectors.prometheus.{ prometheusLayer, publisherLayer, PrometheusPublisher }
import zio.metrics.connectors.MetricsConfig
import zio._

object MetricsApp extends ZIOAppDefault {

  override val bootstrap: ZLayer[ZIOAppArgs, Any, Any] =
    Runtime.removeDefaultLoggers >>> (console(LogFormat.default) ++ logMetrics)

  override def run: ZIO[Scope, Any, ExitCode] =
    (for {
      _            <- ZIO.logInfo("Start")
      _            <- ZIO.logWarning("Some warning")
      _            <- ZIO.logError("Some error")
      _            <- ZIO.logError("Another error")
      _            <- ZIO.sleep(1.second)
      metricValues <- ZIO.serviceWithZIO[PrometheusPublisher](_.get)
      _            <- Console.printLine(metricValues)
      _            <- ZIO.logInfo("Done")
    } yield ExitCode.success)
      .provideLayer((ZLayer.succeed(MetricsConfig(200.millis)) ++ publisherLayer) >+> prometheusLayer)

}
```

Expected Console Output:
```
timestamp=2022-12-20T20:42:59.781481+01:00 level=INFO thread=zio-fiber-6 message="Start"
timestamp=2022-12-20T20:42:59.810161+01:00 level=WARN thread=zio-fiber-6 message="Some warning"
timestamp=2022-12-20T20:42:59.81197+01:00  level=ERROR thread=zio-fiber-6 message="Some error"
timestamp=2022-12-20T20:42:59.813489+01:00 level=ERROR thread=zio-fiber-6 message="Another error"
# TYPE zio_logger_warn_total counter
# HELP zio_logger_warn_total Some help
zio_logger_warn_total 1.0 1671565380643
# TYPE zio_logger_info_total counter
# HELP zio_logger_info_total Some help
zio_logger_info_total 1.0 1671565380643
# TYPE zio_logger_error_total counter
# HELP zio_logger_error_total Some help
zio_logger_error_total 2.0 1671565380643
timestamp=2022-12-20T20:43:00.835177+01:00 level=INFO thread=zio-fiber-6 message="Done"
```