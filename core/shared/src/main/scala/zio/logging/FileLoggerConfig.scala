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

import zio._
import zio.prelude._

import java.net.URI
import java.nio.charset.{ Charset, StandardCharsets }
import java.nio.file.{ Path, Paths }
import scala.util.{ Failure, Success, Try }

/**
 * The settings of a file logger. Pass one to `fileLogger`, `fileJsonLogger` or their async versions, or let
 * those layers read it from configuration with `FileLoggerConfig.load`.
 *
 * {{{
 * import zio._
 * import zio.logging.{ FileLoggerConfig, LogFilter, LogFormat, fileLogger }
 * import java.nio.file.Paths
 *
 * val config: FileLoggerConfig =
 *   FileLoggerConfig(
 *     destination = Paths.get("/var/log/my-app/app.log"),
 *     format = LogFormat.default,
 *     filter = LogFilter.LogLevelByNameConfig(LogLevel.Info),
 *     autoFlushBatchSize = 100,
 *     bufferedIOSize = Some(8192),
 *     rollingPolicy = Some(FileLoggerConfig.FileRollingPolicy.TimeBasedRollingPolicy)
 *   )
 *
 * val bootstrap: ZLayer[Any, Nothing, Unit] = Runtime.removeDefaultLoggers >>> fileLogger(config)
 * }}}
 *
 * @param destination the file to append to; with a `rollingPolicy`, the name that the dated file names are
 *   made from
 * @param format the format of each line
 * @param filter the minimum log level, per logger name
 * @param charset the character encoding of the file
 * @param autoFlushBatchSize the writer is flushed after every `autoFlushBatchSize` lines; `1` flushes after
 *   each line
 * @param bufferedIOSize when set, the file is written through a `java.io.BufferedWriter` with a buffer of
 *   this many characters
 * @param rollingPolicy when set, how the file rolls over to a new file
 * @see [[https://zio.dev/zio-logging/file-logger/ File Logger]]
 * @see [[https://zio.dev/zio-logging/reference-configuration/ Configuration Keys Reference]]
 */
final case class FileLoggerConfig(
  destination: Path,
  format: LogFormat,
  filter: LogFilter.LogLevelByNameConfig,
  charset: Charset = StandardCharsets.UTF_8,
  autoFlushBatchSize: Int = 1,
  bufferedIOSize: Option[Int] = None,
  rollingPolicy: Option[FileLoggerConfig.FileRollingPolicy] = None
) {

  /** Builds the log filter of `filter`. */
  def toFilter[M]: LogFilter[M] = filter.toFilter
}

/**
 * The rolling policies, and the readers that load a file logger config from configuration.
 */
object FileLoggerConfig {

  /** How a file logger moves to a new file. */
  sealed trait FileRollingPolicy

  /** The supported rolling policies, and their config reader. */
  object FileRollingPolicy {

    /**
     * Writes to one file per day. The date, as `yyyy-MM-dd`, is inserted before the file extension of the
     * destination, so `app.log` is written as `app-2026-11-23.log`. The writer switches to the next file at
     * the first line written on a new day, in the system default time zone.
     */
    final case object TimeBasedRollingPolicy extends FileRollingPolicy

    /**
     * Reads the key `type`. The only supported value is `TimeBasedRollingPolicy`; any other value fails.
     */
    val config: Config[FileRollingPolicy] = {
      val tpe       = Config.string("type")
      val timeBased = Config.succeed(TimeBasedRollingPolicy)

      tpe.switch("TimeBasedRollingPolicy" -> timeBased)
    }
  }

  private def charsetValue(value: String): Either[Config.Error.InvalidData, Charset] =
    Try(Charset.forName(value)) match {
      case Success(v) => Right(v)
      case Failure(_) =>
        Left(Config.Error.InvalidData(Chunk.empty, s"Expected a Charset, but found ${value}"))
    }

  private def pathValue(value: String): Either[Config.Error.InvalidData, Path] =
    Try(Paths.get(URI.create(value))) match {
      case Success(v) => Right(v)
      case Failure(_) =>
        Left(Config.Error.InvalidData(Chunk.empty, s"Expected a Path, but found ${value}"))
    }

  /**
   * Reads the keys `path` (required, a URI such as `file:///var/log/app.log`), `format` (a pattern string,
   * default `LogFormat.default`), `filter` (`rootLevel`, default `INFO`, and `mappings`), `charset` (default
   * `UTF-8`), `autoFlushBatchSize` (default `1`), `bufferedIOSize` (optional) and `rollingPolicy` (optional).
   * A plain path such as `/var/log/app.log` is not a valid `path`: it fails with
   * `Expected a Path, but found /var/log/app.log`.
   */
  val config: Config[FileLoggerConfig] = {
    val autoFlushBatchSizeConfig = Config.int.nested("autoFlushBatchSize").withDefault(1)
    val bufferedIOSizeConfig     = Config.int.nested("bufferedIOSize").optional
    val filterConfig             = LogFilter.LogLevelByNameConfig.config.nested("filter")
    val charsetConfig            =
      Config.string.mapOrFail(charsetValue).nested("charset").withDefault(StandardCharsets.UTF_8)
    val pathConfig               = Config.string.mapOrFail(pathValue).nested("path")
    val formatConfig             = LogFormat.config.nested("format").withDefault(LogFormat.default)
    val rollingPolicyConfig      = FileRollingPolicy.config.nested("rollingPolicy").optional

    (pathConfig ++ formatConfig ++ filterConfig ++ charsetConfig ++ autoFlushBatchSizeConfig ++ bufferedIOSizeConfig ++ rollingPolicyConfig).map {
      case (path, format, filter, charset, autoFlushBatchSize, bufferedIOSize, rollingPolicy) =>
        FileLoggerConfig(
          path,
          format,
          filter,
          charset,
          autoFlushBatchSize,
          bufferedIOSize,
          rollingPolicy
        )
    }
  }

  /**
   * Compares two configs by every field: the format with `==`, the filter settings, and the other fields
   * with `==`. Formats read from the same pattern string are equal, except when the pattern has a
   * `%timestamp{...}` element with its own date-time pattern: each read builds a new `DateTimeFormatter`, and
   * two such formats are not equal.
   */
  implicit val equal: Equal[FileLoggerConfig] = Equal.make { (l, r) =>
    l.destination == r.destination &&
    l.charset == r.charset &&
    l.autoFlushBatchSize == r.autoFlushBatchSize &&
    l.bufferedIOSize == r.bufferedIOSize &&
    l.rollingPolicy == r.rollingPolicy &&
    l.format == r.format &&
    l.filter === r.filter
  }

  /**
   * Reads the config under `configPath`, by default `logger`, from the `ConfigProvider` of the running
   * effect. Fails with `Config.Error` when `path` is missing or a value is invalid.
   */
  def load(configPath: NonEmptyChunk[String] = loggerConfigPath): ZIO[Any, Config.Error, FileLoggerConfig] =
    ZIO.config(FileLoggerConfig.config.nested(configPath.head, configPath.tail: _*))

  /** A layer that provides the config read by `load` from `configPath`. */
  def make(
    configPath: NonEmptyChunk[String] = loggerConfigPath
  ): ZLayer[Any, Config.Error, FileLoggerConfig] =
    ZLayer.fromZIO(load(configPath))

}
