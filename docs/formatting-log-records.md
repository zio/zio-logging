---
id: formatting-log-records
title: "Formatting Log Records"
---

A `LogFormat` represents a DSL to describe the format of text log messages.

[//]: # (TODO: make snippet type-checked using mdoc)

```scala
import zio.logging.console
import zio.logging.LogFormat._

val myLogFormat = timestamp.fixed(32) |-| level |-| label("message", quoted(line))
val myConsoleLogger = console(myLogFormat)
```

`LogFormat.filter` returns a new log format that produces the same result, if `LogFilter` is satisfied.

```scala
import zio.logging.LogFormat
import zio.logging.LogFilter

LogFormat.label("cause", LogFormat.cause).filter(LogFilter.causeNonEmpty)
```