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

  def fileAsyncJsonLogger(config: FileLoggerConfig): ZLayer[Any, Nothing, Unit] =
    makeFileAsyncJsonLogger(config).installUnscoped

  def fileAsyncJsonLogger(configPath: NonEmptyChunk[String] = loggerConfigPath): ZLayer[Any, Config.Error, Unit] =
    FileLoggerConfig.load(configPath).flatMap(makeFileAsyncJsonLogger).installUnscoped

  def fileAsyncLogger(config: FileLoggerConfig): ZLayer[Any, Nothing, Unit] =
    makeFileAsyncLogger(config).installUnscoped

  def fileAsyncLogger(configPath: NonEmptyChunk[String] = loggerConfigPath): ZLayer[Any, Config.Error, Unit] =
    FileLoggerConfig.load(configPath).flatMap(makeFileAsyncLogger).installUnscoped

  def fileJsonLogger(config: FileLoggerConfig): ZLayer[Any, Nothing, Unit] =
    makeFileJsonLogger(config).install

  def fileJsonLogger(configPath: NonEmptyChunk[String] = loggerConfigPath): ZLayer[Any, Config.Error, Unit] =
    FileLoggerConfig.load(configPath).flatMap(makeFileJsonLogger).install

  def fileLogger(config: FileLoggerConfig): ZLayer[Any, Nothing, Unit] =
    makeFileLogger(config).install

  def fileLogger(configPath: NonEmptyChunk[String] = loggerConfigPath): ZLayer[Any, Config.Error, Unit] =
    FileLoggerConfig.load(configPath).flatMap(makeFileLogger).install

  def makeFileAsyncJsonLogger(config: FileLoggerConfig): ZIO[Scope, Nothing, FilteredLogger[String, Any]] =
    makeFileAsyncLogger(
      config.destination,
      config.format.toJsonLogger,
      config.charset,
      config.autoFlushBatchSize,
      config.bufferedIOSize,
      config.rollingPolicy
    ).filter(config.toFilter)

  def makeFileAsyncLogger(config: FileLoggerConfig): ZIO[Scope, Nothing, FilteredLogger[String, Any]] =
    makeFileAsyncLogger(
      config.destination,
      config.format.toLogger,
      config.charset,
      config.autoFlushBatchSize,
      config.bufferedIOSize,
      config.rollingPolicy
    ).filter(config.toFilter)

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

  def makeFileJsonLogger(config: FileLoggerConfig): ZIO[Any, Nothing, FilteredLogger[String, Any]] =
    makeFileLogger(
      config.destination,
      config.format.toJsonLogger,
      config.charset,
      config.autoFlushBatchSize,
      config.bufferedIOSize,
      config.rollingPolicy
    ).filter(config.toFilter)

  def makeFileLogger(config: FileLoggerConfig): ZIO[Any, Nothing, FilteredLogger[String, Any]] =
    makeFileLogger(
      config.destination,
      config.format.toLogger,
      config.charset,
      config.autoFlushBatchSize,
      config.bufferedIOSize,
      config.rollingPolicy
    ).filter(config.toFilter)

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
