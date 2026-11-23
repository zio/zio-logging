/*
 * Copyright 2019-2026 John A. De Goes and the ZIO Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package zio.logging

import zio.metrics.Metric
import zio.{ Config, NonEmptyChunk, Scope, Tag, ZIO, ZLayer, ZLogger }

import java.io.PrintStream

private[logging] trait LoggerLayers {

  private[logging] val logLevelMetricLabel = "level"

  private[logging] val loggedTotalMetric = Metric.counter(name = "zio_log_total")

  /**
   * Installs a `MetricLogger` that writes nothing and increments the counter `zio_log_total` for every log
   * event, with the label `level` set to the lower-case level label, such as `info` or `warn`, and the metric
   * tags of the logging fiber.
   *
   * {{{
   * import zio._
   * import zio.logging.{ consoleLogger, logMetrics }
   *
   * val bootstrap: ZLayer[Any, Config.Error, Unit] =
   *   Runtime.removeDefaultLoggers >>> consoleLogger() >>> logMetrics
   * }}}
   *
   * @see [[https://zio.dev/zio-logging/metrics/ Log Metrics]]
   */
  val logMetrics: ZLayer[Any, Nothing, Unit] =
    makeMetricLogger(loggedTotalMetric, logLevelMetricLabel).install

  /**
   * Same as [[logMetrics]], with the counter named `name` and the level label named `logLevelLabel`.
   *
   * @see [[https://zio.dev/zio-logging/metrics/ Log Metrics]]
   */
  def logMetricsWith(name: String, logLevelLabel: String): ZLayer[Any, Nothing, Unit] =
    makeMetricLogger(Metric.counter(name), logLevelLabel).install

  /**
   * Installs a logger that writes text lines, formatted with `config.format`, to standard error, for the
   * events that `config.filter` accepts.
   */
  def consoleErrLogger(config: ConsoleLoggerConfig): ZLayer[Any, Nothing, Unit] =
    makeConsoleErrLogger(config).install

  /**
   * Installs a logger that writes JSON lines, formatted with `config.format`, to standard error, for the
   * events that `config.filter` accepts.
   */
  def consoleErrJsonLogger(config: ConsoleLoggerConfig): ZLayer[Any, Nothing, Unit] =
    makeConsoleErrJsonLogger(config).install

  /**
   * Same as the `consoleErrJsonLogger` overload that takes a config, with the `ConsoleLoggerConfig` read
   * from `configPath` by `ConsoleLoggerConfig.load`. Fails with `Config.Error` when the config is invalid.
   */
  def consoleErrJsonLogger(configPath: NonEmptyChunk[String] = loggerConfigPath): ZLayer[Any, Config.Error, Unit] =
    ConsoleLoggerConfig.load(configPath).flatMap(makeConsoleErrJsonLogger).install

  /**
   * Same as the `consoleErrLogger` overload that takes a config, with the `ConsoleLoggerConfig` read from
   * `configPath` by `ConsoleLoggerConfig.load`. Fails with `Config.Error` when the config is invalid.
   */
  def consoleErrLogger(configPath: NonEmptyChunk[String] = loggerConfigPath): ZLayer[Any, Config.Error, Unit] =
    ConsoleLoggerConfig.load(configPath).flatMap(makeConsoleErrLogger).install

  /**
   * Installs a logger that writes JSON lines, formatted with `config.format`, to standard output, for the
   * events that `config.filter` accepts.
   *
   * {{{
   * import zio._
   * import zio.logging.{ ConsoleLoggerConfig, LogFilter, LogFormat, consoleJsonLogger }
   *
   * val config: ConsoleLoggerConfig =
   *   ConsoleLoggerConfig(
   *     LogFormat.label("level", LogFormat.level) + LogFormat.label("message", LogFormat.line),
   *     LogFilter.LogLevelByNameConfig(LogLevel.Info)
   *   )
   *
   * val bootstrap: ZLayer[Any, Nothing, Unit] = Runtime.removeDefaultLoggers >>> consoleJsonLogger(config)
   * // ZIO.logInfo("Started") is written as: {"level":"INFO","message":"Started"}
   * }}}
   *
   * @see [[https://zio.dev/zio-logging/how-to-json-logs/ JSON Logs]]
   */
  def consoleJsonLogger(config: ConsoleLoggerConfig): ZLayer[Any, Nothing, Unit] =
    makeConsoleJsonLogger(config).install

  /**
   * Same as the `consoleJsonLogger` overload that takes a config, with the `ConsoleLoggerConfig` read
   * from `configPath` by `ConsoleLoggerConfig.load`. Fails with `Config.Error` when the config is invalid.
   */
  def consoleJsonLogger(configPath: NonEmptyChunk[String] = loggerConfigPath): ZLayer[Any, Config.Error, Unit] =
    ConsoleLoggerConfig.load(configPath).flatMap(makeConsoleJsonLogger).install

  /**
   * Installs a logger that writes text lines, formatted with `config.format`, to standard output, for the
   * events that `config.filter` accepts. Installing adds this logger to the loggers in use; ZIO's default
   * logger keeps running unless the layer comes after `Runtime.removeDefaultLoggers`.
   *
   * {{{
   * import zio._
   * import zio.logging.{ ConsoleLoggerConfig, LogFilter, LogFormat, consoleLogger }
   *
   * val config: ConsoleLoggerConfig =
   *   ConsoleLoggerConfig(LogFormat.colored, LogFilter.LogLevelByNameConfig(LogLevel.Debug))
   *
   * object Main extends ZIOAppDefault {
   *   override val bootstrap: ZLayer[ZIOAppArgs, Any, Any] =
   *     Runtime.removeDefaultLoggers >>> consoleLogger(config)
   *
   *   def run: UIO[Unit] = ZIO.logDebug("Starting")
   * }
   * }}}
   *
   * @see [[https://zio.dev/zio-logging/console-logger/ Console Logger]]
   */
  def consoleLogger(config: ConsoleLoggerConfig): ZLayer[Any, Nothing, Unit] =
    makeConsoleLogger(config).install

  /**
   * Same as the `consoleLogger` overload that takes a config, with the `ConsoleLoggerConfig` read from
   * `configPath`, by default `logger`, through `ConsoleLoggerConfig.load`. Fails with `Config.Error` when the
   * config is invalid; keys that are not set take their defaults.
   *
   * {{{
   * import zio._
   * import zio.logging.consoleLogger
   *
   * val provider: ConfigProvider =
   *   ConfigProvider.fromMap(
   *     Map(
   *       "logger/format"           -> "%timestamp %level [%fiberId] %name:%line %message %cause",
   *       "logger/filter/rootLevel" -> "INFO"
   *     ),
   *     "/"
   *   )
   *
   * val bootstrap: ZLayer[Any, Config.Error, Unit] =
   *   Runtime.removeDefaultLoggers >>> Runtime.setConfigProvider(provider) >>> consoleLogger()
   * }}}
   *
   * @see [[https://zio.dev/zio-logging/console-logger/ Console Logger]]
   * @see [[https://zio.dev/zio-logging/reference-configuration/ Configuration Keys Reference]]
   */
  def consoleLogger(configPath: NonEmptyChunk[String] = loggerConfigPath): ZLayer[Any, Config.Error, Unit] =
    ConsoleLoggerConfig.load(configPath).flatMap(makeConsoleLogger).install

  /**
   * Builds, without installing, the logger of `consoleErrLogger`: text lines on standard error, filtered by
   * `config.filter`.
   */
  def makeConsoleErrLogger(config: ConsoleLoggerConfig): ZIO[Any, Nothing, ZLogger[String, Any]] =
    makeSystemErrLogger(config.format.toLogger).filter(config.toFilter)

  /**
   * Builds, without installing, the logger of `consoleErrJsonLogger`: JSON lines on standard error, filtered
   * by `config.filter`.
   */
  def makeConsoleErrJsonLogger(config: ConsoleLoggerConfig): ZIO[Any, Nothing, ZLogger[String, Any]] =
    makeSystemErrLogger(config.format.toJsonLogger).filter(config.toFilter)

  /**
   * Builds, without installing, the logger of `consoleLogger`: text lines on standard output, filtered by
   * `config.filter`.
   */
  def makeConsoleLogger(config: ConsoleLoggerConfig): ZIO[Any, Nothing, ZLogger[String, Any]] =
    makeSystemOutLogger(config.format.toLogger).filter(config.toFilter)

  /**
   * Builds, without installing, the logger of `consoleJsonLogger`: JSON lines on standard output, filtered by
   * `config.filter`.
   */
  def makeConsoleJsonLogger(config: ConsoleLoggerConfig): ZIO[Any, Nothing, ZLogger[String, Any]] =
    makeSystemOutLogger(config.format.toJsonLogger).filter(config.toFilter)

  /**
   * Same as `makePrintStreamLogger` with `System.out`, read when this method is called. No filter is applied.
   */
  def makeSystemOutLogger(
    logger: ZLogger[String, String]
  ): ZIO[Any, Nothing, ZLogger[String, Any]] = makePrintStreamLogger(logger, java.lang.System.out)

  /**
   * Same as `makePrintStreamLogger` with `System.err`, read when this method is called. No filter is applied.
   */
  def makeSystemErrLogger(
    logger: ZLogger[String, String]
  ): ZIO[Any, Nothing, ZLogger[String, Any]] = makePrintStreamLogger(logger, java.lang.System.err)

  /**
   * Builds a logger that formats each event with `logger`, usually `LogFormat#toLogger` or
   * `LogFormat#toJsonLogger`, and writes the result to `stream` with `println`. No filter is applied.
   * Exceptions from `println`, other than a `VirtualMachineError`, are ignored.
   *
   * {{{
   * import zio._
   * import zio.logging.{ LogFormat, makePrintStreamLogger }
   * import java.io.{ ByteArrayOutputStream, PrintStream }
   *
   * val buffer = new ByteArrayOutputStream()
   * val capture: ZLayer[Any, Nothing, Unit] =
   *   makePrintStreamLogger(LogFormat.level.toLogger, new PrintStream(buffer, true)).install
   * // after ZIO.logInfo("x") runs with this layer, buffer holds the line INFO
   * }}}
   */
  def makePrintStreamLogger(
    logger: ZLogger[String, String],
    stream: PrintStream
  ): ZIO[Any, Nothing, ZLogger[String, Any]] = ZIO.succeed(printStreamLogger(logger, stream))

  private def printStreamLogger(
    logger: ZLogger[String, String],
    stream: PrintStream
  ): ZLogger[String, Any] = {
    val stringLogger = logger.map { line =>
      try stream.println(line)
      catch {
        case t: VirtualMachineError => throw t
        case _: Throwable           => ()
      }
    }
    stringLogger
  }

  /**
   * Builds, without installing, a `MetricLogger` that increments `counter` for every log event, with the
   * label `logLevelLabel` set to the lower-case level label.
   */
  def makeMetricLogger(counter: Metric.Counter[Long], logLevelLabel: String): ZIO[Any, Nothing, MetricLogger] =
    ZIO.succeed(MetricLogger(counter, logLevelLabel))

  /**
   * Adds `filter` and the `install` methods to an effect that builds a logger, such as the result of
   * `makeConsoleLogger` or `makeFileLogger`.
   *
   * @see [[https://zio.dev/zio-logging/console-logger/ Console Logger]]
   */
  implicit final class ZLoggerZIOLayerOps[RIn, +E, ROut <: ZLogger[String, Any]: Tag](
    private val self: ZIO[RIn, E, ROut]
  ) {

    /** Wraps the built logger in a `FilteredLogger`, which passes on only the events that `filter` accepts. */
    def filter(filter: LogFilter[String]): ZIO[RIn, E, FilteredLogger[String, Any]] =
      self.map(logger => FilteredLogger(logger, filter))

    /**
     * Returns a layer that builds the logger and adds it to the loggers in use while the layer is alive. The
     * logger is removed again when the layer's scope closes.
     */
    def install: ZLayer[RIn, E, Unit] =
      ZLayer.scoped[RIn] {
        self.flatMap { logger =>
          ZIO.withLoggerScoped(logger)
        }
      }

    /**
     * Same as `install`, for a logger effect that needs a `Scope`, such as `makeFileAsyncLogger` or
     * `ReconfigurableLogger.make`: the layer's own scope is given to the effect, so the layer does not need
     * one.
     */
    def installUnscoped[RIn2](implicit ev: RIn2 with Scope =:= RIn): ZLayer[RIn2, E, Unit] =
      ZLayer.scoped[RIn2] {
        self.asInstanceOf[ZIO[RIn2 with Scope, E, ROut]].flatMap { logger =>
          ZIO.withLoggerScoped(logger)
        }
      }

    /**
     * Returns a layer that needs a `Scope` and adds the logger to the loggers in use until that scope closes,
     * not when the layer is released.
     */
    def installScoped: ZLayer[Scope with RIn, E, Unit] =
      ZLayer.fromZIO(self.flatMap { logger =>
        ZIO.withLoggerScoped(logger)
      })

  }

}
