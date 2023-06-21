/*
 * Copyright 2019-2023 John A. De Goes and the ZIO Contributors
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

import zio.Tag
import zio.{ Queue, Runtime, Scope, UIO, ULayer, ZIO, ZLayer, ZLogger }
import zio.metrics.Metric

import java.io.PrintStream
import java.nio.charset.Charset
import java.nio.file.Path

object LoggerLayers {

  def makeConsoleErrLogger(config: ConsoleLoggerConfig): ULayer[ZLogger[String, Any]] =
    makePrintStreamLogger(config.format.toLogger, java.lang.System.err, config.filter)

  def makeConsoleErrJsonLogger(config: ConsoleLoggerConfig): ULayer[ZLogger[String, Any]] =
    makePrintStreamLogger(config.format.toJsonLogger, java.lang.System.err, config.filter)

  def makeConsoleLogger(config: ConsoleLoggerConfig): ULayer[ZLogger[String, Any]] =
    makePrintStreamLogger(config.format.toLogger, java.lang.System.out, config.filter)

  def makeConsoleJsonLogger(config: ConsoleLoggerConfig): ULayer[ZLogger[String, Any]] =
    makePrintStreamLogger(config.format.toJsonLogger, java.lang.System.out, config.filter)

  def makePrintStreamLogger(
    logger: ZLogger[String, String],
    stream: PrintStream,
    logFilter: LogFilter[String]
  ): ULayer[ZLogger[String, Any]] = ZLayer.succeed(printStreamLogger(logger, stream, logFilter))

  private def printStreamLogger(
    logger: ZLogger[String, String],
    stream: PrintStream,
    logFilter: LogFilter[String]
  ): ZLogger[String, Any] = {
    val stringLogger = logFilter.filter(logger.map { line =>
      try stream.println(line)
      catch {
        case t: VirtualMachineError => throw t
        case _: Throwable           => ()
      }
    })
    stringLogger
  }

  def makeFileAsyncJsonLogger(
    config: FileLoggerConfig
  ): ZLayer[Scope, Nothing, ZLogger[String, Any]] = makeFileAsyncLogger(
    config.destination,
    config.format.toJsonLogger,
    config.filter,
    config.charset,
    config.autoFlushBatchSize,
    config.bufferedIOSize,
    config.rollingPolicy
  )

  def makeFileAsyncLogger(
    config: FileLoggerConfig
  ): ZLayer[Scope, Nothing, ZLogger[String, Any]] =
    makeFileAsyncLogger(
      config.destination,
      config.format.toLogger,
      config.filter,
      config.charset,
      config.autoFlushBatchSize,
      config.bufferedIOSize,
      config.rollingPolicy
    )

  def makeFileAsyncLogger(
    destination: Path,
    logger: ZLogger[String, String],
    logFilter: LogFilter[String],
    charset: Charset,
    autoFlushBatchSize: Int,
    bufferedIOSize: Option[Int],
    rollingPolicy: Option[FileLoggerConfig.FileRollingPolicy]
  ): ZLayer[Scope, Nothing, ZLogger[String, Any]] = ZLayer.fromZIO {
    for {
      queue <- Queue.bounded[UIO[Any]](1000)
      _     <- queue.take.flatMap(task => task.ignore).forever.forkScoped
    } yield fileWriterAsyncLogger(
      destination,
      logger,
      logFilter,
      charset,
      autoFlushBatchSize,
      bufferedIOSize,
      queue,
      rollingPolicy
    )
  }

  private def fileWriterAsyncLogger(
    destination: Path,
    logger: ZLogger[String, String],
    logFilter: LogFilter[String],
    charset: Charset,
    autoFlushBatchSize: Int,
    bufferedIOSize: Option[Int],
    queue: Queue[UIO[Any]],
    rollingPolicy: Option[FileLoggerConfig.FileRollingPolicy]
  ): ZLogger[String, Any] = {
    val logWriter =
      new zio.logging.internal.FileWriter(destination, charset, autoFlushBatchSize, bufferedIOSize, rollingPolicy)

    val stringLogger: ZLogger[String, Any] = logFilter.filter(logger.map { (line: String) =>
      zio.Unsafe.unsafe { implicit u =>
        Runtime.default.unsafe.run(queue.offer(ZIO.succeed {
          try logWriter.writeln(line)
          catch {
            case t: VirtualMachineError => throw t
            case _: Throwable           => ()
          }
        }))
      }
    })
    stringLogger
  }

  def makeFileJsonLogger(config: FileLoggerConfig): ULayer[ZLogger[String, Any]] =
    makeFileLogger(
      config.destination,
      config.format.toJsonLogger,
      config.filter,
      config.charset,
      config.autoFlushBatchSize,
      config.bufferedIOSize,
      config.rollingPolicy
    )

  def makeFileLogger(config: FileLoggerConfig): ULayer[ZLogger[String, Any]] =
    makeFileLogger(
      config.destination,
      config.format.toLogger,
      config.filter,
      config.charset,
      config.autoFlushBatchSize,
      config.bufferedIOSize,
      config.rollingPolicy
    )

  def makeFileLogger(
    destination: Path,
    logger: ZLogger[String, String],
    logFilter: LogFilter[String],
    charset: Charset,
    autoFlushBatchSize: Int,
    bufferedIOSize: Option[Int],
    rollingPolicy: Option[FileLoggerConfig.FileRollingPolicy]
  ): ULayer[ZLogger[String, Any]] =
    ZLayer.succeed(
      fileWriterLogger(destination, logger, logFilter, charset, autoFlushBatchSize, bufferedIOSize, rollingPolicy)
    )

  private def fileWriterLogger(
    destination: Path,
    logger: ZLogger[String, String],
    logFilter: LogFilter[String],
    charset: Charset,
    autoFlushBatchSize: Int,
    bufferedIOSize: Option[Int],
    rollingPolicy: Option[FileLoggerConfig.FileRollingPolicy]
  ): ZLogger[String, Any] = {
    val logWriter =
      new zio.logging.internal.FileWriter(destination, charset, autoFlushBatchSize, bufferedIOSize, rollingPolicy)

    val stringLogger: ZLogger[String, Any] = logFilter.filter(logger.map { (line: String) =>
      try logWriter.writeln(line)
      catch {
        case t: VirtualMachineError => throw t
        case _: Throwable           => ()
      }
    })

    stringLogger
  }

  def makeMetricLogger(counter: Metric.Counter[Long], logLevelLabel: String): ULayer[MetricLogger] =
    ZLayer.succeed(MetricLogger(counter, logLevelLabel))

  implicit final class ZLoggerLayerOps[-RIn, +E, ROut <: ZLogger[String, Any]: Tag](
    private val self: ZLayer[RIn, E, ROut]
  ) {

    def install: ZLayer[RIn, E, Unit] =
      self.flatMap { env =>
        ZLayer.scoped {
          ZIO.withLoggerScoped(env.get[ROut])
        }
      }

    def installScoped: ZLayer[Scope with RIn, E, Unit] =
      self.flatMap { env =>
        ZLayer.fromZIO(ZIO.withLoggerScoped(env.get[ROut]))
      }
  }
}
