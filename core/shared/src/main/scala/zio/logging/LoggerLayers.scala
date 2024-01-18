/*
 * Copyright 2019-2024 John A. De Goes and the ZIO Contributors
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
import zio.{ Config, NonEmptyChunk, Queue, Runtime, Scope, Tag, UIO, ZIO, ZLayer, ZLogger }

import java.io.PrintStream
import java.nio.charset.Charset
import java.nio.file.Path

private[logging] trait LoggerLayers {

  private[logging] val logLevelMetricLabel = "level"

  private[logging] val loggedTotalMetric = Metric.counter(name = "zio_log_total")

  val logMetrics: ZLayer[Any, Nothing, Unit] =
    makeMetricLogger(loggedTotalMetric, logLevelMetricLabel).install

  def logMetricsWith(name: String, logLevelLabel: String): ZLayer[Any, Nothing, Unit] =
    makeMetricLogger(Metric.counter(name), logLevelLabel).install

  def consoleErrLogger(config: ConsoleLoggerConfig): ZLayer[Any, Nothing, Unit] =
    makeConsoleErrLogger(config).install

  def consoleErrJsonLogger(config: ConsoleLoggerConfig): ZLayer[Any, Nothing, Unit] =
    makeConsoleErrJsonLogger(config).install

  def consoleErrJsonLogger(configPath: NonEmptyChunk[String] = loggerConfigPath): ZLayer[Any, Config.Error, Unit] =
    ConsoleLoggerConfig.load(configPath).flatMap(makeConsoleErrJsonLogger).install

  def consoleErrLogger(configPath: NonEmptyChunk[String] = loggerConfigPath): ZLayer[Any, Config.Error, Unit] =
    ConsoleLoggerConfig.load(configPath).flatMap(makeConsoleErrLogger).install

  def consoleJsonLogger(config: ConsoleLoggerConfig): ZLayer[Any, Nothing, Unit] =
    makeConsoleJsonLogger(config).install

  def consoleJsonLogger(configPath: NonEmptyChunk[String] = loggerConfigPath): ZLayer[Any, Config.Error, Unit] =
    ConsoleLoggerConfig.load(configPath).flatMap(makeConsoleJsonLogger).install

  def consoleLogger(config: ConsoleLoggerConfig): ZLayer[Any, Nothing, Unit] =
    makeConsoleLogger(config).install

  def consoleLogger(configPath: NonEmptyChunk[String] = loggerConfigPath): ZLayer[Any, Config.Error, Unit] =
    ConsoleLoggerConfig.load(configPath).flatMap(makeConsoleLogger).install

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

  def makeConsoleErrLogger(config: ConsoleLoggerConfig): ZIO[Any, Nothing, ZLogger[String, Any]] =
    makeSystemErrLogger(config.format.toLogger).filter(config.toFilter)

  def makeConsoleErrJsonLogger(config: ConsoleLoggerConfig): ZIO[Any, Nothing, ZLogger[String, Any]] =
    makeSystemErrLogger(config.format.toJsonLogger).filter(config.toFilter)

  def makeConsoleLogger(config: ConsoleLoggerConfig): ZIO[Any, Nothing, ZLogger[String, Any]] =
    makeSystemOutLogger(config.format.toLogger).filter(config.toFilter)

  def makeConsoleJsonLogger(config: ConsoleLoggerConfig): ZIO[Any, Nothing, ZLogger[String, Any]] =
    makeSystemOutLogger(config.format.toJsonLogger).filter(config.toFilter)

  def makeSystemOutLogger(
    logger: ZLogger[String, String]
  ): ZIO[Any, Nothing, ZLogger[String, Any]] = makePrintStreamLogger(logger, java.lang.System.out)

  def makeSystemErrLogger(
    logger: ZLogger[String, String]
  ): ZIO[Any, Nothing, ZLogger[String, Any]] = makePrintStreamLogger(logger, java.lang.System.err)

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

  def makeMetricLogger(counter: Metric.Counter[Long], logLevelLabel: String): ZIO[Any, Nothing, MetricLogger] =
    ZIO.succeed(MetricLogger(counter, logLevelLabel))

  implicit final class ZLoggerZIOLayerOps[RIn, +E, ROut <: ZLogger[String, Any]: Tag](
    private val self: ZIO[RIn, E, ROut]
  ) {

    def filter(filter: LogFilter[String]): ZIO[RIn, E, FilteredLogger[String, Any]] =
      self.map(logger => FilteredLogger(logger, filter))

    def install: ZLayer[RIn, E, Unit] =
      ZLayer.scoped[RIn] {
        self.flatMap { logger =>
          ZIO.withLoggerScoped(logger)
        }
      }

    def installUnscoped[RIn2](implicit ev: RIn2 with Scope =:= RIn): ZLayer[RIn2, E, Unit] =
      ZLayer.scoped[RIn2] {
        self.asInstanceOf[ZIO[RIn2 with Scope, E, ROut]].flatMap { logger =>
          ZIO.withLoggerScoped(logger)
        }
      }

    def installScoped: ZLayer[Scope with RIn, E, Unit] =
      ZLayer.fromZIO(self.flatMap { logger =>
        ZIO.withLoggerScoped(logger)
      })

  }

}
