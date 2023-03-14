[//]: # (This file was autogenerated using `zio-sbt-website` plugin via `sbt generateReadme` command.)
[//]: # (So please do not edit it manually. Instead, change "docs/index.md" file or sbt setting keys)
[//]: # (e.g. "readmeDocumentation" and "readmeSupport".)

# zio-logging

[ZIO Logging](https://github.com/zio/zio-logging) is simple logging for ZIO apps, with correlation, context, and pluggable backends out of the box with integrations for common logging backends.

[![Production Ready](https://img.shields.io/badge/Project%20Stage-Production%20Ready-brightgreen.svg)](https://github.com/zio/zio/wiki/Project-Stages) ![CI Badge](https://github.com/zio/zio-logging/workflows/CI/badge.svg) [![Sonatype Releases](https://img.shields.io/nexus/r/https/oss.sonatype.org/dev.zio/zio-logging_2.13.svg?label=Sonatype%20Release)](https://oss.sonatype.org/content/repositories/releases/dev/zio/zio-logging_2.13/) [![Sonatype Snapshots](https://img.shields.io/nexus/s/https/oss.sonatype.org/dev.zio/zio-logging_2.13.svg?label=Sonatype%20Snapshot)](https://oss.sonatype.org/content/repositories/snapshots/dev/zio/zio-logging_2.13/) [![javadoc](https://javadoc.io/badge2/dev.zio/zio-logging-docs_2.13/javadoc.svg)](https://javadoc.io/doc/dev.zio/zio-logging-docs_2.13) [![zio-logging](https://img.shields.io/github/stars/zio/zio-logging?style=social)](https://github.com/zio/zio-logging)

## Introduction

When we are writing our applications using ZIO effects, to log easy way we need a ZIO native solution for logging. ZIO Logging is an environmental effect for adding logging into our ZIO applications.

Key features of ZIO Logging:

- **ZIO Native** — Other than it is a type-safe and purely functional solution, it leverages ZIO's features.
- **Multi-Platform** - It supports both JVM and JS platforms.
- **Composable** — Loggers are composable together via contraMap.
- **Pluggable Backends** — Support multiple backends like ZIO Console, SLF4j, JS Console, JS HTTP endpoint.
- **Logger Context** — It has a first citizen _Logger Context_ implemented on top of `FiberRef`. The Logger Context maintains information like logger name, filters, correlation id, and so forth across different fibers. It supports _Mapped Diagnostic Context (MDC)_ which manages contextual information across fibers in a concurrent environment.
- Richly integrated into ZIO 2's built-in logging facilities
- ZIO Console, SLF4j, and other backends

## Installation

In order to use this library, we need to add the following line in our `build.sbt` file:

```scala
libraryDependencies += "dev.zio" %% "zio-logging" % "2.1.11"
```

There are also some optional dependencies:

```scala
// JPL integration
libraryDependencies += "dev.zio" %% "zio-logging-jpl" % "2.1.11"

// SLF4j v1 integration
libraryDependencies += "dev.zio" %% "zio-logging-slf4j" % "2.1.11"

// SLF4j v2 integration
libraryDependencies += "dev.zio" %% "zio-logging-slf4j2" % "2.1.11"

// Using ZIO Logging for SLF4j v1 loggers, usually third-party non-ZIO libraries
libraryDependencies += "dev.zio" %% "zio-logging-slf4j-bridge" % "2.1.11"

// Using ZIO Logging for SLF4j v2 loggers, usually third-party non-ZIO libraries
libraryDependencies += "dev.zio" %% "zio-logging-slf4j2-bridge" % "2.1.11"
```

## Example

Let's try an example of ZIO Logging which demonstrates a simple application of ZIO logging.

The recommended place for setting the logger is application boostrap. In this case, custom logger will be set for whole application runtime (also application failures will be logged with specified logger).

[//]: # (TODO: make snippet type-checked using mdoc)

```scala
import zio.logging.{ LogFormat, console }
import zio.{ ExitCode, Runtime, Scope, ZIO, ZIOAppArgs, ZIOAppDefault, ZLayer }

object SimpleApp extends ZIOAppDefault {

  override val bootstrap: ZLayer[ZIOAppArgs, Any, Any] =
    Runtime.removeDefaultLoggers >>> console(LogFormat.default)

  override def run: ZIO[Scope, Any, ExitCode] =
    for {
      _ <- ZIO.logInfo("Start")
      _ <- ZIO.fail("FAILURE")
      _ <- ZIO.logInfo("Done")
    } yield ExitCode.success

}
```

Expected console output:

```
timestamp=2022-10-28T18:40:25.517623+02:00 level=INFO thread=zio-fiber-6 message="Start"
timestamp=2022-10-28T18:40:25.54676+02:00  level=ERROR thread=zio-fiber-0 message="" cause=Exception in thread "zio-fiber-6" java.lang.String: FAILURE
	at zio.logging.example.SimpleApp.run(SimpleApp.scala:14)
```

You can find the source code of examples [here](https://github.com/zio/zio-logging/tree/master/examples)

## Documentation

Learn more on the [zio-logging homepage](https://zio.dev/zio-logging)!

## Contributing

For the general guidelines, see ZIO [contributor's guide](https://zio.dev/about/contributing).

## Code of Conduct

See the [Code of Conduct](https://zio.dev/about/code-of-conduct)

## Support

Come chat with us on [![Badge-Discord]][Link-Discord].

[Badge-Discord]: https://img.shields.io/discord/629491597070827530?logo=discord "chat on discord"
[Link-Discord]: https://discord.gg/2ccFBr4 "Discord"

## License

[License](LICENSE)
