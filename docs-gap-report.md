# ZIO Logging Documentation Gap Report

Audit date: 2026-11-17. Base: `origin/master` 0c40eb86cdd (after #1176 and #1185).
Scope: every published module (`zio-logging` core for JVM/JS/Native, `zio-logging-slf4j`, `zio-logging-slf4j2`, `zio-logging-slf4j-bridge`, `zio-logging-slf4j2-bridge`, `zio-logging-jul-bridge`, `zio-logging-jpl`) against the 23 pages in `docs/`.

This file is an internal tracking document. It lives at the repository root on purpose, so the docs site does not publish it.

## How the audit was done

1. The `scan-undocumented.sh` scanner from the `docs-find-documentation-gaps` skill listed public types and whether any page names them.
2. Every main source file and every docs page was then read in full, in three groups: core formatting and filtering, core loggers and configuration, and the integration modules. Each public member was checked against the pages, and each statement on a page was checked against the code.
3. Behaviour that looked wrong in the code was checked by reading the code, and for the formatting and filter code by running small scripts against the published `zio-logging` 2.5.4 jar. The published `zio-logging-slf4j-bridge_2.13` 2.5.4 jar was inspected to confirm its Java class-file version.

## Summary

| Metric | Count |
|---|---|
| Public types found by the scanner | 61 |
| ... named on some page | 21 (34%) |
| ... that users actually touch (after removing ADT cases, `private[logging]` code and SPI classes) | 24 |
| ... of those, with no mention at all | 6: `LogContext`, `FilteredLogger`, `MetricLogger`, `LogFormat.Pattern`, `LogColor` (values), `ReconfigurableLogger` (manual API) |
| Public members (methods, layers, aspects, values) with no mention | about 60 (listed below) |
| Statements on existing pages that are wrong or outdated | 35 |
| Pages that are too thin to be useful on their own | 6 (`logger-context-and-annotations`, `testing`, `metrics`, `log-filter`, `reconfigurable-logger`, the four bridge pages counted as one) |
| Possible library bugs found along the way (reported separately, not fixed here) | 29 |

The scanner's 34% understates coverage for types and overstates it for members: most of the 40 "undocumented types" are the cases of `LogFormat.Pattern` or SPI classes that SLF4J and JUL load by name, while whole families of methods on documented types (the `make*` constructors, the `install*` helpers, the bridge variants) have no mention.

## Critical: missing reference coverage

These are user-facing APIs with no page, or with only a passing use in an example.

### Core (`zio.logging` package object, `core/shared/src/main/scala/zio/logging/LoggerLayers.scala`, `LoggingPackageAllPlatforms.scala`, `core/jvm-native/src/main/scala/zio/logging/FileLoggerLayers.scala`)

`LoggerLayers` and `FileLoggerLayers` are `private[logging]` traits mixed into `package object logging`, so all of the following are public as `zio.logging.<name>`.

- [ ] **Logger constructors (ZIO effects, not layers)**: `makeConsoleLogger`, `makeConsoleJsonLogger`, `makeConsoleErrLogger`, `makeConsoleErrJsonLogger`, `makeSystemOutLogger`, `makeSystemErrLogger`, `makePrintStreamLogger(logger, stream)`, `makeMetricLogger(counter, label)`, `makeFileLogger`, `makeFileJsonLogger`, `makeFileAsyncLogger`, `makeFileAsyncJsonLogger` (config and 6-argument forms). `makeConsoleLogger` and `makeSystemOutLogger` are used on `reconfigurable-logger.md` but never explained. *New reference page.*
- [ ] **Installing a logger**: the `ZLoggerZIOLayerOps` syntax `.filter`, `.install`, `.installUnscoped`, `.installScoped` (LoggerLayers.scala:101-127), and `ZLoggerOps.filter(LogFilter)` returning `FilteredLogger` (LoggingPackageAllPlatforms.scala:88-94). *Same page.*
- [ ] **`consoleErrLogger` / `consoleErrJsonLogger`**: one line each on `console-logger.md`, no example. *Same page.*
- [ ] **Logger-name aspects**: `loggerName(fn: Option[String] => String)` (LoggingPackageAllPlatforms.scala:63) and `appendLoggerName(value)` (:80, added in #893). `loggerName(String)` itself is only used in examples, never explained. *Section in the annotations reference.*
- [ ] **`LogContext`** (`LogContext.scala`): `++`, `annotate`, `apply`, `get[A]`, `get(name)`, `merge`, `asMap`, `empty`. The word `LogContext` appears nowhere in `docs/`. *Annotations reference.*
- [ ] **`LogAnnotation`** (`LogAnnotation.scala`): `TraceId` (UUID) is used in examples but never described; `TraceSpans` (list, rendered with `:`) is not mentioned anywhere; the `combine` function of a custom annotation is not explained; `effect.logAnnotate(annotation, value)` (LoggingPackageAllPlatforms.scala:83-86) is not mentioned. *Annotations reference.*
- [ ] **`LogFormat.Pattern`** (LogFormat.scala:399-774): the parsed form of string patterns, with `Pattern.parse`, `Pattern.config`, `toLogFormat`. The page names a `LogFormat.parse` that does not exist (see fixes). *Section in `reference-log-format.md`.*
- [ ] **`LogColor`** (`LogColor.scala`): the Scala values `RED`, `BLUE`, `YELLOW`, `CYAN`, `GREEN`, `MAGENTA`, `WHITE`, `RESET` and `LogColor.config`. *Section in `reference-log-format.md`.*
- [ ] **`ReconfigurableLogger` manual API**: `ReconfigurableLogger.apply(config, logger)`, `get`, `set` (ReconfigurableLogger.scala:23-58), the `C: Equal` requirement of `make` and what the `Option[ZLogger]` argument means. *Reference section on `reconfigurable-logger.md`.*
- [ ] **`FilteredLogger`** (`FilteredLogger.scala`) and **`MetricLogger`** (`MetricLogger.scala`): `FilteredLogger` is the return type of every `make*FileLogger`. *Brief entries on the logger-constructors page.*
- [ ] **Config descriptors and `Equal` instances**: `ConsoleLoggerConfig.default`, `.config`, `.equal`; `FileLoggerConfig.config`, `.equal`; `FileRollingPolicy.config`; `LogFilter.equal`; `LogLevelByNameConfig.equal`. *Rows in `reference-configuration.md`.*

### Backends (`slf4j/`, `slf4j2/`, `jpl/`)

- [ ] `SLF4J.slf4j(format)`, `slf4j(format, loggerName, causeToThrowable)`, `slf4jLogger(...)` (slf4j SLF4J.scala:202-225), `logFormatDefault`, `causeToThrowableDefault` (wraps the cause in `FiberFailure`), `getLoggerName(default = "zio-slf4j-logger")`, `logMarkerNameAnnotationKey`. *Reference section on `slf4j2.md` / `slf4j1.md`.*
- [ ] Level mapping (`Fatal` to `ERROR`, `All` to `TRACE`, `None` dropped) and the fact that the SLF4J provider, not a ZIO `LogFilter`, decides which levels are written. *Same.*
- [ ] `JPL.jpl(format)`, `jpl(format, loggerName)`, `jplLogger(...)` (JPL.scala:95-109), level mapping, fallback name `zio-jpl-logger`. *Reference section on `jpl.md`.*

### Bridges (`slf4j-bridge/`, `slf4j2-bridge/`, `jul-bridge/`)

- [ ] `Slf4jBridge.initializeWithoutFiberRefPropagation`, `initWithoutFiberRefPropagation(filter)`, `make(filter)`, `make(configPath)` (slf4j-bridge Slf4jBridge.scala:47-73, same in slf4j2-bridge). Not mentioned anywhere; nor is the fact that `init*` turns on `Runtime.enableCurrentFiber`.
- [ ] `JULBridge.initializeWithoutFiberRefPropagation`, `initWithoutFiberRefPropagation`, `make(...)` (JULBridge.scala:50-84).
- [ ] JUL level mapping: `SEVERE` becomes `Fatal`, `CONFIG` becomes `Info`, `FINE`/`FINER` become `Debug`, `FINEST` becomes `Trace` (jul-bridge ZioLoggerRuntime.scala:92-102).
- [ ] What the bridges drop: SLF4J markers (v1 ZioLoggerBase.java:21, v2 ZioLogger.scala:20-27), MDC values set by Java code (v1 StaticMDCBinder.java:27-29, v2 ZioSLF4JServiceProvider.java:56). Only v2 key-value pairs survive, as annotations.
- [ ] What the bridge filter can see: only the `logger_name` annotation and the level, because it runs in `isEnabled` before the message exists (v1 ZioLoggerRuntime.scala:73-86). Filters on fiber annotations or message text never match there.
- [ ] Each bridged record gets an extra log span named after the Java logger (v1 ZioLoggerRuntime.scala:47-50).

SPI classes that need no user documentation, only one sentence saying why they are public: `org.slf4j.impl.StaticLoggerBinder`, `StaticMDCBinder`, `StaticMarkerBinder`, `org.slf4j.helpers.ZioLoggerBase`, `ZioLogger`, `ZioLoggerFactory`, `LoggerRuntime`, `LoggerData`, the three `ZioLoggerRuntime` classes, `ZioSLF4JServiceProvider` (worth naming for the `slf4j.provider` system property), and the JPMS module `org.slf4j.zio`.

## High priority: wrong or outdated statements on existing pages

### Core pages

| # | Page:line | Problem | Code |
|---|---|---|---|
| 1 | reference-log-format.md:172 | Names `LogFormat.parse`, which does not exist. The method is `LogFormat.Pattern.parse`. | LogFormat.scala:765 |
| 2 | reference-log-format.md:40, :44 | "the ANSI code for `LogFormat#color`" and "when `LogFormat#filter` accepts": garbled self-references; they should name the `color` and `filter` arguments. | LogFormat.scala:63-75 |
| 3 | reference-log-format.md:17, 25, 85 | By-name signatures (`text(value: => String)` and so on) suggest the value is computed per line; it is computed once when the format is built. Disagrees with `formatting-log-records.md:120-123`, whose `make`-based `text` is computed per line. | LogFormat.scala:886, 913, 917 |
| 4 | reference-log-format.md:169 | `default` writes the timestamp in a 32-character column, which cuts the time-zone offset on JDKs with nanosecond clocks (observed `...915147744+03`). The page should say so. | LogFormat.scala:920 |
| 5 | reference-log-filter.md:113 | "use [LogGroup] on its own, for example to build a formatted label": no API turns a `LogGroup` into a `LogFormat`. | LogGroup.scala |
| 6 | reference-log-filter.md:25-34, 147-150, 176-178 | Instance methods written as `LogFilter.and`, `LogGroup.map`, which reads as companion members; `#` and bare names are mixed. | — |
| 7 | how-to-json-logs.md:39 | "Without a label, the value is ... plain text, which cannot be read as a JSON field": unlabelled text goes into a `"text_content"` field. | LogAppender.scala:219-224 |
| 8 | how-to-json-logs.md:61 | "string values ... are escaped, so ... still produces one valid JSON object": values that are themselves JSON are embedded raw, surrounding quotes are stripped, blank values become `""`. | LogAppender.scala:185-206 |
| 9 | formatting-log-records.md:42 | `%kvs` is "all annotations": it leaves out `logger_name`. | LogFormat.scala:477 |
| 10 | formatting-log-records.md:36 | Color list omits `RESET`; names are case-sensitive in patterns (`%color{red}` fails) but not in config. | LogColor.scala:42-46, LogFormat.scala:656 |
| 11 | log-filter.md:23-24 | Calls glob patterns "prefixes". | LogFilter.scala:420-455 |
| 12 | console-logger.md:45, file-logger.md:51 | The comment showing the default format as a pattern is only an approximation (the real default quotes the message and prints the cause only when present). | LogFormat.scala:919-924 |
| 13 | reference-configuration.md:11 | "logger layers such as `LoggerLayers.consoleLogger`": `LoggerLayers` is a `private[logging]` trait; the public name is `zio.logging.consoleLogger`. | LoggerLayers.scala:23 |
| 14 | how-to-log-levels-from-config.md:25, how-to-file-logging-rotation.md:25 | `"zio-config-typesafe" % "@VERSION@"` gives zio-logging's version (2.5.x); zio-config is on 4.x. | project/Versions.scala:10 |
| 15 | how-to-file-logging-rotation.md:147 | "If the directory is missing ... log lines disappear without an error": at startup the layer dies with a `FileNotFoundException` defect. | FileLoggerLayers.scala:144-157, WriterProvider.scala:36,54 |
| 16 | testing.md:10 | `% ZioVersion % Test`: undefined placeholder. | — |
| 17 | testing.md:13 and the snippet after it | "It replaces the default loggers" and the need to provide `ZTestLogger.default`: it only adds a logger, and ZIO Test already installs it around every test. | zio-test ZTestLogger.scala:38-43, TestExecutor.scala:165-167 |
| 18 | reconfigurable-logger.md:223 | "once there is an official stable `zio-http` release": zio-http 3.x is stable. | project/Versions.scala:13 |
| 19 | index.md:31, tutorial.md:172 | Only `%%` is shown, although JS and Native are supported (`%%%`). | build.sbt:78 |
| 20 | index.md:98 | Platform table omits `fileAsyncLogger` and `fileAsyncJsonLogger`. | FileLoggerLayers.scala:25-35 |
| 21 | tutorial.md:175 | "you also need ZIO itself": zio-logging already depends on ZIO. | build.sbt:82 |
| 22 | index.md:107 | Typo "boostrap". | — |

### Integration pages

| # | Page:line | Problem | Code |
|---|---|---|---|
| 23 | slf4j1.md:6, 15; slf4j1-bridge.md:6 | "working with JDK8": the published 2.5.4 jars are Java 17 class files (major version 61), and CI tests only 17/21/25. | `zio-logging-slf4j-bridge_2.13-2.5.4.jar` StaticLoggerBinder.class; .github/workflows/ci.yml:87-90 |
| 24 | slf4j2.md:6, 16; slf4j2-bridge.md:6 | "using JDK9+ module system JPMS" / "working with JDK9+": the backend has no module-info; minimum is Java 17. Only the v2 bridge ships `module-info`. | slf4j2-bridge/src/main/java/module-info.java |
| 25 | slf4j1.md:18-19 | MDC "will be reset by different Fiber before the message is logged": the backend sets MDC, logs and restores it in one synchronous block on one thread. The real caveats (it replaces the thread's MDC; with no annotations, the thread's MDC leaks in) are not stated. | slf4j SLF4J.scala:140-181 |
| 26 | slf4j2-bridge.md:138, slf4j1-bridge.md:123 | Expected output shows a `DEBUG ... "Done"` line that the example's own filter drops (`"zio.logging.slf4j" -> Debug` does not match `zio.logging.example.*`). | LogFilter.scala:375, 465 |
| 27 | jul-bridge.md:94-98 | Example filter maps `"zio.logging.slf4j"` and `"SLF4J-LOGGER"`, copied from the SLF4J page; the logger is `JUL-LOGGER`, so the mappings do nothing. | jul-bridge.md:92 |
| 28 | jul-bridge.md:132 | Typo `JULbridgeExampleApp`; time zones truncated to `+02:0`. | — |
| 29 | jul-bridge.md:6 | "do not confuse with `java.platform.logging`": no such package; JPL is `java.lang.System.Logger`. | JPL.scala:44 |
| 30 | index.md:87 | Describes the JUL bridge as if it were a backend. index.md:80 says "one of following lines" but lists one. | — |
| 31 | how-to-bridge-java-logs.md:55 | Shows a "custom path" that is the default path. | Slf4jBridge.scala:24 |
| 32 | how-to-bridge-java-logs.md:42 | Reason given for `>+>` is inaccurate: the bridge captures the runtime when its layer is built, so the logger layer must be built first. | Slf4jBridge.scala:66, ZioLoggerRuntime.scala:41-42 |
| 33 | how-to-bridge-java-logs.md:98 | "Other JUL handlers may receive more lines" understates it: `readConfiguration` removes every existing handler and level. | JULBridge.scala:73-74 |
| 34 | formatting-log-records.md:95-97 | Only the SLF4J v1 MDC behaviour is described; v2 uses key-value pairs. | slf4j2 SLF4J.scala:118-148 |
| 35 | how-to-bridge-java-logs.md:18 | Mentions the v1 bridge without linking its page. | — |

## Medium priority: thin pages

- [ ] **logger-context-and-annotations.md** (24 lines): no output example; `combine` unexplained; nothing on `TraceId`, `TraceSpans`, `logAnnotate`, `LogContext`, reading or setting `logContext`, the difference from ZIO's string annotations (`ZIO.logAnnotate`), the `loggerName`/`appendLoggerName` aspects, or how annotations reach the output (`logAnnotation`, `allAnnotations`, `%kv`).
- [ ] **testing.md** (75 lines, one snippet): needs what `ZTestLogger.logOutput` returns and the `LogEntry` fields; string annotations versus `context.get(logContext)`; rendering an entry with a `LogFormat` (`entry.call(format.toLogger)`); capturing console output with `makePrintStreamLogger`; testing a `LogFilter` directly; config-driven tests with `TestSystem.putProperty` and `ConfigProvider.fromProps`; that `ZTestLogger` records every call regardless of zio-logging filters.
- [ ] **metrics.md** (94 lines): needs the metric type (a `Long` counter), that every fiber metric tag becomes an extra label (`ZIOAspect.tagged`), that counts ignore the console filter, the connector dependency and setup, reading the counter in tests, `makeMetricLogger` for custom counters, and a re-checked Prometheus sample.
- [ ] **log-filter.md** (59 lines): glob rules (`*`, `**`, `X*Y`) live only in code comments; precedence (most specific mapping wins) is undocumented; no HOCON example with quoted glob keys; no `filter.filter(logger)` example.
- [ ] **reconfigurable-logger.md**: `C: Equal`, the previous logger passed to `makeLogger`, when the first reload happens, what happens when a reload fails (the reload loop stops), and the push-style `apply` / `set` / `get` API.
- [ ] **Bridge pages** (`slf4j1-bridge.md`, `slf4j2-bridge.md`, `jul-bridge.md`): the config keys for filtering (`logger.filter.rootLevel`, `logger.filter.mappings`) are only on the how-to page; nothing on markers, MDC, the without-propagation variants, logs emitted before init (silently dropped), or the JUL global side effects.
- [ ] **file-logger.md**: async loggers use a bounded queue of 1000; callers block when it is full; queued lines can be lost at shutdown; the writer is not flushed when the scope closes.

## Conceptual gaps

- [ ] **Second tutorial**: the existing tutorial stops at console JSON and a code-level filter. A follow-on tutorial for a small service (logger names, annotations per request, levels from config, JSON to a file, metrics, a test that checks the log output) would give newcomers a learning path into the rest of the docs.
- [ ] **How-to: test log output** (assert on what was logged and on the rendered line).
- [ ] **How-to: add a correlation or trace ID to every log line** (`LogAnnotation.TraceId`, custom annotations, `@@` on a request handler).
- [ ] **How-to: use ZIO Logging with Logback or Log4j 2 through SLF4J** (choosing v1 or v2: Logback 1.2 goes with v1, Logback 1.3+ and `log4j-slf4j2-impl` with v2; `%X` versus `%kvp`; the provider decides levels).
- [ ] **How-to: change log levels at runtime without a restart** (`ReconfigurableLogger` from config, and pushed with `set`).
- [ ] **How-to: write a custom log format** (combinators first, `LogFormat.make` only when needed).
- [ ] **Mixing modules** (one section, probably on the bridge how-to): a bridge conflicts with every other SLF4J provider on the classpath; the v1 bridge is silently ignored when `slf4j-api` 2.x is on the classpath; the JPL backend plus the JUL bridge make a loop.
- Migration guide from zio-logging 0.5.x/1.x to 2.x: out of scope (2.x has been current since 2022); not proposed.

## Low priority / skip

- `zio.logging.internal.*` (`JsonEscape`, `JsonValidator`, `LogAppender`, `FileWriter`, `WriterProvider`): internal, excluded from MiMa (project/MimaSettings.scala:14). `LogAppender` gets one paragraph only because it is the first parameter of the public `LogFormat.make`.
- The cases of `LogFormat.Pattern` (`Arg`, `Text`, `Label`, `Fixed`, `Color`, `Highlight`, ...): covered as one table on the pattern syntax, not one entry each.
- The SPI classes listed above.
- Build note, not a page: the `slf4j1*.md` mdoc blocks compile against the v2 modules (build.sbt:305-306), and unidoc covers `coreJVM`, `slf4j`, `slf4jBridge` and `jpl` only (build.sbt:300).

## Possible library bugs (not fixed in this work)

Sent to the coordinator as separate entries. Where the docs describe the affected behaviour, they will describe what the code does today.

| # | Area | Summary | Where |
|---|---|---|---|
| 1 | LogFilter | A trailing `*` inside a name segment never matches (`org.my.Serv*` does not match `org.my.ServiceImpl`); verified. | LogFilter.scala:433-451 |
| 2 | LogFilter | `*suffix` matches the first occurrence, not the end. | same |
| 3 | JSON | The JSON appender can emit invalid JSON when a label mixes text and a JSON value; verified. | LogAppender.scala:185-187, 270-272 |
| 4 | JSON | Leading/trailing `"` stripped from any value; blank values cleared; verified. | LogAppender.scala:197-206 |
| 5 | LogFormat | By-name `text`, `label`, `timestamp(fmt)` are forced once at construction; verified. | LogFormat.scala:886, 913, 917 |
| 6 | LogFormat | `default`/`colored` cut the timestamp offset with `fixed(32)`; observed. | LogFormat.scala:920, 927 |
| 7 | LogFormat | `%color{red}` fails, `%color{RED}` works; config upper-cases. | LogFormat.scala:656, LogColor.scala:46 |
| 8 | LogFormat | Default highlighter colors FATAL white. | LogFormat.scala:171-177 |
| 9 | LogFormat | `enclosingClass` writes the file name. | LogFormat.scala:846 |
| 10 | LogFormat | `allAnnotations` can emit duplicate JSON keys. | LogFormat.scala:359-365 |
| 11 | LogContext | `get(name)`/`asMap` ambiguous for same-name annotations of different types. | LogContext.scala:53-57, 82-85 |
| 12 | LogFilter | Minor: `cached` re-wraps; config parser drops error detail; `ConfiguredFilter` equality ignores the group. | LogFilter.scala:68-72, 226; LogFormat.scala:771, 781 |
| 13 | File | Sync file logger writes line and separator in two unsynchronised calls; concurrent lines may interleave. | FileWriter.scala:41-55 |
| 14 | File | Writer never flushed or closed when the scope ends; buffered lines lost. | FileLoggerLayers.scala:99-100, 156-157 |
| 15 | File | Async logger drops queued lines on shutdown and can block callers forever. | FileLoggerLayers.scala:78-79, 104 |
| 16 | File | `autoFlushBatchSize = 0` accepted; every line throws (swallowed), never flushes. | FileLoggerLayers.scala:160-164, FileLoggerConfig.scala:69 |
| 17 | File | Rolling policy NPE on a relative path built in code. | WriterProvider.scala:79-85 |
| 18 | Reconfig | One failed reload stops all later reloads silently; verified by reading. | ReconfigurableLogger.scala:69-76 |
| 19 | Reconfig | The replaced logger is never closed (file handles leak). | ReconfigurableLogger.scala:72-73 |
| 20 | Console | `System.out`/`err` captured once; later `System.setOut` ignored. | LoggerLayers.scala:73, 77 |
| 21 | File | Missing directory surfaces as a defect, not a config error. | FileLoggerLayers.scala:144-157 |
| 22 | JUL | Parameterised messages not formatted (`Hi {0}` logged literally); verified by reading. | jul-bridge ZioLoggerRuntime.scala:31 |
| 23 | JUL | Custom levels throw `NoSuchElementException`. | jul-bridge ZioLoggerRuntime.scala:36, 75, 92-102 |
| 24 | JUL | Anonymous logger (null name) likely NPE in the name filter. | jul-bridge ZioLoggerRuntime.scala:48, 85; LogFilter.scala:465 |
| 25 | SLF4J v2 bridge | `addKeyValue("k", null)` throws NPE. | slf4j2-bridge ZioLoggerRuntime.scala:50 |
| 26 | Bridges | Another SLF4J provider on the classpath gives a `ClassCastException` defect at init. | slf4j2-bridge ZioLoggerFactory.scala:51-53; slf4j-bridge :52-53 |
| 27 | Bridges | `runtime` field not volatile. | ZioLoggerFactory.scala:25-26 |
| 28 | SLF4J v2 bridge | `module-info` exports nothing and does not require zio or scala-library. | module-info.java:1-4 |
| 29 | JPL | `LogLevel.None` maps to `OFF`, which a JUL-backed logger may still emit. | JPL.scala:32 |

## Suggested actions, grouped as proposed pull requests

### PR 1: fixes to existing pages (update existing)
- [ ] Fix the 35 wrong or outdated statements above.

### PR 2: reference (new pages and new sections)
- [ ] New page `reference-logger-layers.md`: every layer, `make*` constructor, `install*` helper, `filter` syntax, `FilteredLogger`, `MetricLogger`, platform availability.
- [ ] New page `reference-log-annotations.md`: `LogAnnotation` (predefined and custom, `combine`), `LogContext`, `logContext`, `logAnnotate`, the `loggerName` / `appendLoggerName` aspects.
- [ ] New page `reference-integrations.md` (or one reference section per integration page): SLF4J and JPL backend overloads, level mappings, bridge entry points including the without-propagation variants, what each bridge keeps and drops, SPI classes.
- [ ] New sections in `reference-log-format.md`: `LogFormat.Pattern`, `LogColor`, the pattern syntax rules (escaping, `%label` with `%cause`, `%kvs` exclusion).
- [ ] New rows in `reference-configuration.md`: config descriptors and `Equal` instances.
- [ ] New reference section in `reconfigurable-logger.md`: `make` in full, `apply` / `get` / `set`.

### PR 3: thin pages (enrich existing)
- [ ] `logger-context-and-annotations.md`, `testing.md`, `metrics.md`, `log-filter.md`, `reconfigurable-logger.md`, the three bridge pages, `file-logger.md` (async behaviour).

### PR 4: tutorials and how-to guides (new pages)
- [ ] Tutorial: logging for a small service.
- [ ] How-to: test log output.
- [ ] How-to: add a correlation ID to every log line.
- [ ] How-to: use ZIO Logging with Logback or Log4j 2.
- [ ] How-to: change log levels at runtime.
- [ ] How-to: write a custom log format.
- [ ] Sidebar and `index.md` wiring.
