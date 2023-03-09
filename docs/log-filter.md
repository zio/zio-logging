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
    "io.grpc.netty" -> LogLevel.Info
)
```

will use the `Debug` log level for everything except for log events with the logger name
prefixed by either `List("io", "netty")` or `List("io", "grpc", "netty")`.
Logger name is extracted from log annotation or `zio.Trace`.

`LogFilter.filter` returns a version of `zio.ZLogger` that only logs messages when this filter is satisfied.


## Configuration

the configuration for filter (`zio.logging.LogFilter.LogLevelByNameConfig`) has the following configuration structure:

```
{
    # LogLevel values: ALL, FATAL, ERROR, WARN, INFO, DEBUG, TRACE, OFF
    
    # root LogLevel, default value: INFO
    rootLevel = DEBUG 
    
    # LogLevel configurations for specific logger names, or prefixes, default value: empty
    mappings {
      "io.netty" = "INFO"
      "io.grpc.netty" = "INFO"
    }
}
```

this configuration is equivalent to following:

```scala
import zio.LogLevel
import zio.logging.LogFilter

val config =
  LogFilter.LogLevelByNameConfig(LogLevel.Debug, Map("io.netty" -> LogLevel.Info, "io.grpc.netty" -> LogLevel.Info))

val filter = LogFilter.logLevelByName(config)    
```