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

import zio.{ Config, NonEmptyChunk, Queue, Runtime, Scope, UIO, ZIO, ZLayer, ZLogger }

import java.nio.charset.Charset
import java.nio.file.Path

private[logging] trait FileLoggerLayers {

  /**
   * Same as `fileAsyncLogger`, with each line formatted as JSON by `config.format.toJsonLogger`.
   *
   * @see [[https://zio.dev/zio-logging/file-logger/ File Logger]]
   */
  def fileAsyncJsonLogger(config: FileLoggerConfig): ZLayer[Any, Nothing, Unit] =
    makeFileAsyncJsonLogger(config).installUnscoped

  /**
   * Same as the `fileAsyncJsonLogger` overload that takes a config, with the `FileLoggerConfig` read from
   * `configPath` by `FileLoggerConfig.load`. Fails with `Config.Error` when the config is missing or invalid.
   */
  def fileAsyncJsonLogger(configPath: NonEmptyChunk[String] = loggerConfigPath): ZLayer[Any, Config.Error, Unit] =
    FileLoggerConfig.load(configPath).flatMap(makeFileAsyncJsonLogger).installUnscoped

  /**
   * Installs a logger that appends text lines, formatted with `config.format`, to the file
   * `config.destination`, for the events that `config.filter` accepts. The logging fiber only queues each
   * line; a background fiber, forked when the layer is built, writes the lines to the file in order.
   *
   * {{{
   * import zio._
   * import zio.logging.{ FileLoggerConfig, LogFilter, LogFormat, fileAsyncLogger }
   * import java.nio.file.Paths
   *
   * val config: FileLoggerConfig =
   *   FileLoggerConfig(Paths.get("/var/log/my-app/app.log"), LogFormat.default, LogFilter.LogLevelByNameConfig.default)
   *
   * val bootstrap: ZLayer[Any, Nothing, Unit] = Runtime.removeDefaultLoggers >>> fileAsyncLogger(config)
   * }}}
   *
   * @see [[https://zio.dev/zio-logging/file-logger/ File Logger]]
   */
  def fileAsyncLogger(config: FileLoggerConfig): ZLayer[Any, Nothing, Unit] =
    makeFileAsyncLogger(config).installUnscoped

  /**
   * Same as the `fileAsyncLogger` overload that takes a config, with the `FileLoggerConfig` read from
   * `configPath` by `FileLoggerConfig.load`. Fails with `Config.Error` when the config is missing or invalid.
   */
  def fileAsyncLogger(configPath: NonEmptyChunk[String] = loggerConfigPath): ZLayer[Any, Config.Error, Unit] =
    FileLoggerConfig.load(configPath).flatMap(makeFileAsyncLogger).installUnscoped

  /**
   * Same as `fileLogger`, with each line formatted as JSON by `config.format.toJsonLogger`.
   *
   * @see [[https://zio.dev/zio-logging/file-logger/ File Logger]]
   * @see [[https://zio.dev/zio-logging/how-to-json-logs/ JSON Logs]]
   */
  def fileJsonLogger(config: FileLoggerConfig): ZLayer[Any, Nothing, Unit] =
    makeFileJsonLogger(config).install

  /**
   * Same as the `fileJsonLogger` overload that takes a config, with the `FileLoggerConfig` read from
   * `configPath` by `FileLoggerConfig.load`. Fails with `Config.Error` when the config is missing or invalid.
   */
  def fileJsonLogger(configPath: NonEmptyChunk[String] = loggerConfigPath): ZLayer[Any, Config.Error, Unit] =
    FileLoggerConfig.load(configPath).flatMap(makeFileJsonLogger).install

  /**
   * Installs a logger that appends text lines, formatted with `config.format`, to the file
   * `config.destination`, for the events that `config.filter` accepts. Each line is written by the fiber that
   * logs, during the log call; each line and its line separator are written separately, so lines that
   * several fibers log at the same time can be mixed together in the file, while `fileAsyncLogger` writes
   * from a single fiber. The file is opened when the layer is built; when it cannot be opened, for
   * example because its directory does not exist, building the layer dies with a
   * `java.io.FileNotFoundException`.
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
   *     filter = LogFilter.LogLevelByNameConfig(LogLevel.Debug)
   *   )
   *
   * val bootstrap: ZLayer[Any, Nothing, Unit] = Runtime.removeDefaultLoggers >>> fileLogger(config)
   * }}}
   *
   * @see [[https://zio.dev/zio-logging/file-logger/ File Logger]]
   */
  def fileLogger(config: FileLoggerConfig): ZLayer[Any, Nothing, Unit] =
    makeFileLogger(config).install

  /**
   * Same as the `fileLogger` overload that takes a config, with the `FileLoggerConfig` read from
   * `configPath`, by default `logger`, through `FileLoggerConfig.load`. Fails with `Config.Error` when the
   * config is missing or invalid, for example when `path` is not set.
   *
   * @see [[https://zio.dev/zio-logging/file-logger/ File Logger]]
   * @see [[https://zio.dev/zio-logging/reference-configuration/ Configuration Keys Reference]]
   */
  def fileLogger(configPath: NonEmptyChunk[String] = loggerConfigPath): ZLayer[Any, Config.Error, Unit] =
    FileLoggerConfig.load(configPath).flatMap(makeFileLogger).install

  /**
   * Builds, without installing, the logger of `fileAsyncJsonLogger`, filtered by `config.filter`. The
   * background writer fiber is forked into the given `Scope`.
   */
  def makeFileAsyncJsonLogger(config: FileLoggerConfig): ZIO[Scope, Nothing, FilteredLogger[String, Any]] =
    makeFileAsyncLogger(
      config.destination,
      config.format.toJsonLogger,
      config.charset,
      config.autoFlushBatchSize,
      config.bufferedIOSize,
      config.rollingPolicy
    ).filter(config.toFilter)

  /**
   * Builds, without installing, the logger of `fileAsyncLogger`, filtered by `config.filter`. The background
   * writer fiber is forked into the given `Scope`.
   */
  def makeFileAsyncLogger(config: FileLoggerConfig): ZIO[Scope, Nothing, FilteredLogger[String, Any]] =
    makeFileAsyncLogger(
      config.destination,
      config.format.toLogger,
      config.charset,
      config.autoFlushBatchSize,
      config.bufferedIOSize,
      config.rollingPolicy
    ).filter(config.toFilter)

  /**
   * Builds, without installing, an asynchronous file logger that formats each event with `logger`, usually
   * `LogFormat#toLogger` or `LogFormat#toJsonLogger`, and has a background fiber, forked into the given
   * `Scope`, append the lines to `destination`. No filter is applied. The other arguments mean the same as
   * the `FileLoggerConfig` fields of the same names.
   */
  def makeFileAsyncLogger(
    destination: Path,
    logger: ZLogger[String, String],
    charset: Charset,
    autoFlushBatchSize: Int,
    bufferedIOSize: Option[Int],
    rollingPolicy: Option[FileLoggerConfig.FileRollingPolicy]
  ): ZIO[Scope, Nothing, ZLogger[String, Any]] =
    for {
      queue <- Queue.bounded[UIO[Any]](1000)
      _     <- queue.take.flatMap(task => task.ignore).forever.forkScoped
    } yield fileWriterAsyncLogger(
      destination,
      logger,
      charset,
      autoFlushBatchSize,
      bufferedIOSize,
      queue,
      rollingPolicy
    )

  private def fileWriterAsyncLogger(
    destination: Path,
    logger: ZLogger[String, String],
    charset: Charset,
    autoFlushBatchSize: Int,
    bufferedIOSize: Option[Int],
    queue: Queue[UIO[Any]],
    rollingPolicy: Option[FileLoggerConfig.FileRollingPolicy]
  ): ZLogger[String, Any] = {
    val logWriter =
      new zio.logging.internal.FileWriter(destination, charset, autoFlushBatchSize, bufferedIOSize, rollingPolicy)

    val stringLogger: ZLogger[String, Any] = logger.map { (line: String) =>
      zio.Unsafe.unsafe { implicit u =>
        Runtime.default.unsafe.run(queue.offer(ZIO.succeed {
          try logWriter.writeln(line)
          catch {
            case t: VirtualMachineError => throw t
            case _: Throwable           => ()
          }
        }))
      }
    }
    stringLogger
  }

  /** Builds, without installing, the logger of `fileJsonLogger`, filtered by `config.filter`. */
  def makeFileJsonLogger(config: FileLoggerConfig): ZIO[Any, Nothing, FilteredLogger[String, Any]] =
    makeFileLogger(
      config.destination,
      config.format.toJsonLogger,
      config.charset,
      config.autoFlushBatchSize,
      config.bufferedIOSize,
      config.rollingPolicy
    ).filter(config.toFilter)

  /** Builds, without installing, the logger of `fileLogger`, filtered by `config.filter`. */
  def makeFileLogger(config: FileLoggerConfig): ZIO[Any, Nothing, FilteredLogger[String, Any]] =
    makeFileLogger(
      config.destination,
      config.format.toLogger,
      config.charset,
      config.autoFlushBatchSize,
      config.bufferedIOSize,
      config.rollingPolicy
    ).filter(config.toFilter)

  /**
   * Builds, without installing, a file logger that formats each event with `logger`, usually
   * `LogFormat#toLogger` or `LogFormat#toJsonLogger`, and appends the line to `destination` from the fiber
   * that logs. The file is opened when this effect runs. No filter is applied. The other arguments mean the
   * same as the `FileLoggerConfig` fields of the same names.
   */
  def makeFileLogger(
    destination: Path,
    logger: ZLogger[String, String],
    charset: Charset,
    autoFlushBatchSize: Int,
    bufferedIOSize: Option[Int],
    rollingPolicy: Option[FileLoggerConfig.FileRollingPolicy]
  ): ZIO[Any, Nothing, ZLogger[String, Any]] =
    ZIO.succeed(
      fileWriterLogger(destination, logger, charset, autoFlushBatchSize, bufferedIOSize, rollingPolicy)
    )

  private def fileWriterLogger(
    destination: Path,
    logger: ZLogger[String, String],
    charset: Charset,
    autoFlushBatchSize: Int,
    bufferedIOSize: Option[Int],
    rollingPolicy: Option[FileLoggerConfig.FileRollingPolicy]
  ): ZLogger[String, Any] = {
    val logWriter =
      new zio.logging.internal.FileWriter(destination, charset, autoFlushBatchSize, bufferedIOSize, rollingPolicy)

    val stringLogger: ZLogger[String, Any] = logger.map { (line: String) =>
      try logWriter.writeln(line)
      catch {
        case t: VirtualMachineError => throw t
        case _: Throwable           => ()
      }
    }

    stringLogger
  }

}
