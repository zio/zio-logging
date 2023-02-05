---
id: metrics
title: "Log Metrics"
---

Log metrics collecting metrics related to ZIO logging (all `ZIO.log*` functions).
As ZIO core supporting multiple loggers, this logging metrics collector is implemented as specific `ZLogger`
which is responsible just for collecting metrics of all logs - `ZIO.log*` functions.

The Metrics layer

```scala
val layer = zio.logging.logMetrics
```

will add a default metric named `zio_log_total` with the label `level` which will be
incremented for each log message with the value of `level` being the corresponding log level label in lower case.

Metrics:

* `LogLevel.All` -> `all`
* `LogLevel.Fatal` -> `fatal`
* `LogLevel.Error` -> `error`
* `LogLevel.Warning` -> `warn`
* `LogLevel.Info` -> `info`
* `LogLevel.Debug` -> `debug`
* `LogLevel.Trace` -> `trace`
* `LogLevel.None` -> `off`

Custom names for the metric and label can be set via:

```scala
val layer = zio.logging.logMetricsWith("log_counter", "log_level")
```

## Examples

You can find the source
code [here](https://github.com/zio/zio-logging/tree/master/examples)

### Console logger with metrics

[//]: # (TODO: make snippet type-checked using mdoc)

```scala
package zio.logging.example

import zio.logging.{ LogFormat, console, logMetrics }
import zio.metrics.connectors.MetricsConfig
import zio.metrics.connectors.prometheus.{ PrometheusPublisher, prometheusLayer, publisherLayer }
import zio.{ ExitCode, Runtime, Scope, ZIO, ZIOAppArgs, ZIOAppDefault, ZLayer, _ }

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
timestamp=2022-12-28T09:43:29.226711+01:00 level=INFO thread=zio-fiber-6 message="Start"
timestamp=2022-12-28T09:43:29.255915+01:00 level=WARN thread=zio-fiber-6 message="Some warning"
timestamp=2022-12-28T09:43:29.257454+01:00 level=ERROR thread=zio-fiber-6 message="Some error"
timestamp=2022-12-28T09:43:29.258267+01:00 level=ERROR thread=zio-fiber-6 message="Another error"
# TYPE zio_log_total counter
# HELP zio_log_total Some help
zio_log_total{level="error"}  2.0 1672217010080
# TYPE zio_log_total counter
# HELP zio_log_total Some help
zio_log_total{level="warn"}  1.0 1672217010080
# TYPE zio_log_total counter
# HELP zio_log_total Some help
zio_log_total{level="info"}  1.0 1672217010080
timestamp=2022-12-28T09:43:30.281274+01:00 level=INFO thread=zio-fiber-6 message="Done"
```