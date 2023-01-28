---
id: log-filter
title: "Log Filter"
---

A `LogFilter` represents function/conditions for log filtering.

Following filter

[//]: # (TODO: make snippet type-checked using mdoc)

```scala
import zio.LogLevel
import zio.logging.LogFilter

val filter = LogFilter.logLevelByName(
    LogLevel.Debug,
    "io.netty" -> LogLevel.Info, 
    io.grpc.netty" -> LogLevel.Info
)
```

will use the `Debug` log level for everything except for log events with the logger name
prefixed by either `List("io", "netty")` or `List("io", "grpc", "netty")`.
Logger name is extracted from log annotation or `zio.Trace`.

`LogFilter.filter` returns a version of `zio.ZLogger` that only logs messages when this filter is satisfied.
