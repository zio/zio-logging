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

import zio.prelude._
import zio.{ Config, NonEmptyChunk, ZIO, ZLayer }

/**
 * The settings of a console logger: the `format` of each line, and the `filter` that sets the minimum log
 * level, per logger name. Pass one to `consoleLogger` or `consoleJsonLogger`, or let those layers read it
 * from configuration with `ConsoleLoggerConfig.load`.
 *
 * {{{
 * import zio._
 * import zio.logging.{ ConsoleLoggerConfig, LogFilter, LogFormat, consoleLogger }
 *
 * val config: ConsoleLoggerConfig =
 *   ConsoleLoggerConfig(
 *     format = LogFormat.colored,
 *     filter = LogFilter.LogLevelByNameConfig(LogLevel.Info, "com.example.db" -> LogLevel.Debug)
 *   )
 *
 * val bootstrap: ZLayer[Any, Nothing, Unit] = Runtime.removeDefaultLoggers >>> consoleLogger(config)
 * }}}
 *
 * @see [[https://zio.dev/zio-logging/console-logger/ Console Logger]]
 * @see [[https://zio.dev/zio-logging/reference-configuration/ Configuration Keys Reference]]
 */
final case class ConsoleLoggerConfig(
  format: LogFormat,
  filter: LogFilter.LogLevelByNameConfig
) {

  /** Builds the log filter of `filter`. */
  def toFilter[M]: LogFilter[M] = filter.toFilter
}

/**
 * The default console logger settings, and the readers that load them from configuration.
 */
object ConsoleLoggerConfig {

  /** `LogFormat.default`, with root level INFO and no per-name levels. */
  val default: ConsoleLoggerConfig = ConsoleLoggerConfig(LogFormat.default, LogFilter.LogLevelByNameConfig.default)

  /**
   * Reads the keys `format` (a pattern string, default `LogFormat.default`) and `filter` (`rootLevel`,
   * default `INFO`, and the `mappings` table, default empty). With none of the keys set, the result equals
   * `default`.
   */
  val config: Config[ConsoleLoggerConfig] = {
    val formatConfig = LogFormat.config.nested("format").withDefault(LogFormat.default)
    val filterConfig = LogFilter.LogLevelByNameConfig.config.nested("filter")
    (formatConfig ++ filterConfig).map { case (format, filter) =>
      ConsoleLoggerConfig(
        format,
        filter
      )
    }
  }

  /**
   * Compares two configs by their format, with `==`, and their filter settings. Formats read from the same
   * pattern string are equal, except when the pattern has a `%timestamp{...}` element with its own date-time
   * pattern: each read builds a new `DateTimeFormatter`, and two such formats are not equal. Two reads of a
   * config with a `%timestamp{pattern}` are never equal, so `ReconfigurableLogger.make` rebuilds the logger on every reload.
   */
  implicit val equal: Equal[ConsoleLoggerConfig] = Equal.make { (l, r) =>
    l.format == r.format && l.filter === r.filter
  }

  /**
   * Reads the config under `configPath`, by default `logger`, from the `ConfigProvider` of the running
   * effect. Fails with `Config.Error` when a value is invalid, such as a format string that does not parse.
   */
  def load(configPath: NonEmptyChunk[String] = loggerConfigPath): ZIO[Any, Config.Error, ConsoleLoggerConfig] =
    ZIO.config(ConsoleLoggerConfig.config.nested(configPath.head, configPath.tail: _*))

  /** A layer that provides the config read by `load` from `configPath`. */
  def make(
    configPath: NonEmptyChunk[String] = loggerConfigPath
  ): ZLayer[Any, Config.Error, ConsoleLoggerConfig] =
    ZLayer.fromZIO(load(configPath))

}
