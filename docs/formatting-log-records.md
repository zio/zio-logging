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

## LogFormat and LogAppender

A `LogFormat` represents a DSL to describe the format of text log messages.

A `LogAppender` is a low-level interface designed to be the bridge between, ZIO Logging and logging backends, such as Logback. 
This interface is slightly higher-level than a string builder, because it allows for structured logging,
and preserves all ZIO-specific information about runtime failures.


`LogFormat` may be created by following function:

```scala
object LogFormat {
  def make(format: (LogAppender, Trace, FiberId, LogLevel, () => String, Cause[Any], FiberRefs, List[LogSpan], Map[String, String]) => Any): LogFormat
}
```

format function arguments can be split to two sections:
* LogAppender
* all others - all log inputs provided by ZIO core logging:
  * Trace - current trace (`zio.Trace`)
  * FiberId - fiber id (`zio.FiberId`)
  * LogLevel - log level (`zio.LogLevel`)
  * () => String - log message
  * Cause[Any] - cause (`zio.Cause`)
  * FiberRefs - fiber refs (`zio.FiberRefs`), collection of `zio.FiberRef` - ZIO's equivalent of Java's ThreadLocal
  * List[LogSpan] - log spans  (`zio.LogSpan`)
  * Map[String, String] - ZIO core log annotations values, where key is annotation key/name, and value is annotation value

essential `LogAppender` functions, which are used in predefined log formats: 

* `def appendCause(cause: Cause[Any])` - appends a `zio.Cause` to the log, some logging backends may have special support for logging failures 
* `def appendNumeric[A](numeric: A)` - appends a numeric value to the log
* `def appendText(text: String)` - appends unstructured text to the log
* `def appendKeyValue(key: String, value: String)` - appends a key/value string pair to the log

then it depends on the specific logging backend how these functions are implemented with respect to the backend output, for example:
* [slf4j v1](slf4j1.md) logging backend - key/value is appended to slf4j [MDC context](https://logback.qos.ch/manual/mdc.html), Cause is transformed to Throwable and placed to slf4j throwable section, all other text and numeric parts are added to slf4j log message
* console logging backend - in general all values are added to log line, `Cause.prettyPrint` is used to log cause details


example of some predefined log formats implementations:

```scala
def annotation(name: String): LogFormat =
  LogFormat.make { (builder, _, _, _, _, _, _, _, annotations) =>
    annotations.get(name).foreach { value =>
      builder.appendKeyValue(name, value)
    }
  }

val cause: LogFormat =
  LogFormat.make { (builder, _, _, _, _, cause, _, _, _) =>
    if (!cause.isEmpty) {
      builder.appendCause(cause)
    }
  }

def text(value: => String): LogFormat =
  LogFormat.make { (builder, _, _, _, _, _, _, _, _) =>
    builder.appendText(value)
  }
```
