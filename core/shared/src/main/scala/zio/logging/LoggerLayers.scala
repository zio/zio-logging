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

import zio.metrics.Metric
import zio.{ Queue, Runtime, Scope, Tag, UIO, ULayer, ZIO, ZLayer, ZLogger }

import java.io.PrintStream
import java.nio.charset.Charset
import java.nio.file.Path

object LoggerLayers {
//
//  def makeConsoleErrLogger(config: ConsoleLoggerConfig): ULayer[ZLogger[String, Any]] =
//    makeSystemErrLogger(config.format.toLogger).project(logger => FilteredLogger(logger, config.filter))
//
//  def makeConsoleErrLogger: ZLayer[ConsoleLoggerConfig, Nothing, ZLogger[String, Any]] =
//    ZLayer.environment[ConsoleLoggerConfig].flatMap { env =>
//      makeConsoleErrLogger(env.get[ConsoleLoggerConfig])
//    }
//
//  def makeConsoleErrJsonLogger(config: ConsoleLoggerConfig): ULayer[ZLogger[String, Any]] =
//    makeSystemErrLogger(config.format.toJsonLogger).project(logger => FilteredLogger(logger, config.filter))
//
//  def makeConsoleErrJsonLogger: ZLayer[ConsoleLoggerConfig, Nothing, ZLogger[String, Any]] =
//    ZLayer.environment[ConsoleLoggerConfig].flatMap { env =>
//      makeConsoleErrJsonLogger(env.get[ConsoleLoggerConfig])
//    }
//
//  def makeConsoleLogger(config: ConsoleLoggerConfig): ULayer[ZLogger[String, Any]] =
//    makeSystemOutLogger(config.format.toLogger).project(logger => FilteredLogger(logger, config.filter))
//
//  def makeConsoleLogger: ZLayer[ConsoleLoggerConfig, Nothing, ZLogger[String, Any]] =
//    ZLayer.environment[ConsoleLoggerConfig].flatMap { env =>
//      makeConsoleLogger(env.get[ConsoleLoggerConfig])
//    }
//
//  def makeConsoleJsonLogger(config: ConsoleLoggerConfig): ULayer[ZLogger[String, Any]] =
//    makeSystemOutLogger(config.format.toJsonLogger).project(logger => FilteredLogger(logger, config.filter))
//
//  def makeConsoleJsonLogger: ZLayer[ConsoleLoggerConfig, Nothing, ZLogger[String, Any]] =
//    ZLayer.environment[ConsoleLoggerConfig].flatMap { env =>
//      makeConsoleJsonLogger(env.get[ConsoleLoggerConfig])
//    }
//
//  def makeSystemOutLogger(
//    logger: ZLogger[String, String]
//  ): ULayer[ZLogger[String, Any]] = makePrintStreamLogger(logger, java.lang.System.out)
//
//  def makeSystemErrLogger(
//    logger: ZLogger[String, String]
//  ): ULayer[ZLogger[String, Any]] = makePrintStreamLogger(logger, java.lang.System.err)
//
//  def makePrintStreamLogger(
//    logger: ZLogger[String, String],
//    stream: PrintStream
//  ): ULayer[ZLogger[String, Any]] = ZLayer.succeed(printStreamLogger(logger, stream))

  def makeConsoleErrLogger(config: ConsoleLoggerConfig): ZIO[Any, Nothing, ZLogger[String, Any]] =
    makeSystemErrLogger(config.format.toLogger).map(logger => FilteredLogger(logger, config.filter))

  def makeConsoleErrLogger: ZIO[ConsoleLoggerConfig, Nothing, ZLogger[String, Any]] =
    ZIO.serviceWithZIO[ConsoleLoggerConfig](makeConsoleErrLogger(_))

  def makeConsoleErrJsonLogger(config: ConsoleLoggerConfig): ZIO[Any, Nothing, ZLogger[String, Any]] =
    makeSystemErrLogger(config.format.toJsonLogger).map(logger => FilteredLogger(logger, config.filter))

  def makeConsoleErrJsonLogger: ZIO[ConsoleLoggerConfig, Nothing, ZLogger[String, Any]] =
    ZIO.serviceWithZIO[ConsoleLoggerConfig](makeConsoleErrJsonLogger(_))

  def makeConsoleLogger(config: ConsoleLoggerConfig): ZIO[Any, Nothing, ZLogger[String, Any]] =
    makeSystemOutLogger(config.format.toLogger).map(logger => FilteredLogger(logger, config.filter))

  def makeConsoleLogger: ZIO[ConsoleLoggerConfig, Nothing, ZLogger[String, Any]] =
    ZIO.serviceWithZIO[ConsoleLoggerConfig](makeConsoleLogger(_))

  def makeConsoleJsonLogger(config: ConsoleLoggerConfig): ZIO[Any, Nothing, ZLogger[String, Any]] =
    makeSystemOutLogger(config.format.toJsonLogger).map(logger => FilteredLogger(logger, config.filter))

  def makeConsoleJsonLogger: ZIO[ConsoleLoggerConfig, Nothing, ZLogger[String, Any]] =
    ZIO.serviceWithZIO[ConsoleLoggerConfig](makeConsoleJsonLogger(_))

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
//
//  def makeFileAsyncJsonLogger(
//    config: FileLoggerConfig
//  ): ZLayer[Scope, Nothing, ZLogger[String, Any]] = makeFileAsyncLogger(
//    config.destination,
//    config.format.toJsonLogger,
//    config.charset,
//    config.autoFlushBatchSize,
//    config.bufferedIOSize,
//    config.rollingPolicy
//  ).project(logger => FilteredLogger(logger, config.filter))
//
//  def makeFileAsyncJsonLogger: ZLayer[FileLoggerConfig with Scope, Nothing, ZLogger[String, Any]] =
//    ZLayer.environment[FileLoggerConfig].flatMap { env =>
//      makeFileAsyncJsonLogger(env.get[FileLoggerConfig])
//    }
//
//  def makeFileAsyncLogger(
//    config: FileLoggerConfig
//  ): ZLayer[Scope, Nothing, ZLogger[String, Any]] =
//    makeFileAsyncLogger(
//      config.destination,
//      config.format.toLogger,
//      config.charset,
//      config.autoFlushBatchSize,
//      config.bufferedIOSize,
//      config.rollingPolicy
//    ).project(logger => FilteredLogger(logger, config.filter))
//
//  def makeFileAsyncLogger: ZLayer[FileLoggerConfig with Scope, Nothing, ZLogger[String, Any]] =
//    ZLayer.environment[FileLoggerConfig].flatMap { env =>
//      makeFileAsyncLogger(env.get[FileLoggerConfig])
//    }
//
//  def makeFileAsyncLogger(
//    destination: Path,
//    logger: ZLogger[String, String],
//    charset: Charset,
//    autoFlushBatchSize: Int,
//    bufferedIOSize: Option[Int],
//    rollingPolicy: Option[FileLoggerConfig.FileRollingPolicy]
//  ): ZLayer[Scope, Nothing, ZLogger[String, Any]] = ZLayer.fromZIO {
//    for {
//      queue <- Queue.bounded[UIO[Any]](1000)
//      _     <- queue.take.flatMap(task => task.ignore).forever.forkScoped
//    } yield fileWriterAsyncLogger(
//      destination,
//      logger,
//      charset,
//      autoFlushBatchSize,
//      bufferedIOSize,
//      queue,
//      rollingPolicy
//    )
//  }
//
//  private def fileWriterAsyncLogger(
//    destination: Path,
//    logger: ZLogger[String, String],
//    charset: Charset,
//    autoFlushBatchSize: Int,
//    bufferedIOSize: Option[Int],
//    queue: Queue[UIO[Any]],
//    rollingPolicy: Option[FileLoggerConfig.FileRollingPolicy]
//  ): ZLogger[String, Any] = {
//    val logWriter =
//      new zio.logging.internal.FileWriter(destination, charset, autoFlushBatchSize, bufferedIOSize, rollingPolicy)
//
//    val stringLogger: ZLogger[String, Any] = logger.map { (line: String) =>
//      zio.Unsafe.unsafe { implicit u =>
//        Runtime.default.unsafe.run(queue.offer(ZIO.succeed {
//          try logWriter.writeln(line)
//          catch {
//            case t: VirtualMachineError => throw t
//            case _: Throwable           => ()
//          }
//        }))
//      }
//    }
//    stringLogger
//  }

//  def makeFileJsonLogger(config: FileLoggerConfig): ULayer[ZLogger[String, Any]] =
//    makeFileLogger(
//      config.destination,
//      config.format.toJsonLogger,
//      config.charset,
//      config.autoFlushBatchSize,
//      config.bufferedIOSize,
//      config.rollingPolicy
//    ).project(logger => FilteredLogger(logger, config.filter))
//
//  def makeFileJsonLogger: ZLayer[FileLoggerConfig, Nothing, ZLogger[String, Any]] =
//    ZLayer.environment[FileLoggerConfig].flatMap { env =>
//      makeFileJsonLogger(env.get[FileLoggerConfig])
//    }
//
//  def makeFileLogger(config: FileLoggerConfig): ULayer[ZLogger[String, Any]] =
//    makeFileLogger(
//      config.destination,
//      config.format.toLogger,
//      config.charset,
//      config.autoFlushBatchSize,
//      config.bufferedIOSize,
//      config.rollingPolicy
//    ).project(logger => FilteredLogger(logger, config.filter))
//
//  def makeFileLogger: ZLayer[FileLoggerConfig, Nothing, ZLogger[String, Any]] =
//    ZLayer.environment[FileLoggerConfig].flatMap { env =>
//      makeFileLogger(env.get[FileLoggerConfig])
//    }
//
//  def makeFileLogger(
//    destination: Path,
//    logger: ZLogger[String, String],
//    charset: Charset,
//    autoFlushBatchSize: Int,
//    bufferedIOSize: Option[Int],
//    rollingPolicy: Option[FileLoggerConfig.FileRollingPolicy]
//  ): ULayer[ZLogger[String, Any]] =
//    ZLayer.succeed(
//      fileWriterLogger(destination, logger, charset, autoFlushBatchSize, bufferedIOSize, rollingPolicy)
//    )

  def makeFileJsonLogger(config: FileLoggerConfig): ZIO[Any, Nothing, FilteredLogger[String, Any]] =
    makeFileLogger(
      config.destination,
      config.format.toJsonLogger,
      config.charset,
      config.autoFlushBatchSize,
      config.bufferedIOSize,
      config.rollingPolicy
    ).map(logger => FilteredLogger(logger, config.filter))

  def makeFileJsonLogger: ZIO[FileLoggerConfig, Nothing, ZLogger[String, Any]] =
    ZIO.serviceWithZIO[FileLoggerConfig](makeFileJsonLogger(_))

  def makeFileLogger: ZIO[FileLoggerConfig, Nothing, ZLogger[String, Any]] =
    ZIO.serviceWithZIO[FileLoggerConfig](makeFileLogger(_))

  def makeFileLogger(config: FileLoggerConfig): ZIO[Any, Nothing, FilteredLogger[String, Any]] =
    makeFileLogger(
      config.destination,
      config.format.toLogger,
      config.charset,
      config.autoFlushBatchSize,
      config.bufferedIOSize,
      config.rollingPolicy
    ).map(logger => FilteredLogger(logger, config.filter))

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

//  def makeMetricLogger(counter: Metric.Counter[Long], logLevelLabel: String): ULayer[MetricLogger] =
//    ZLayer.succeed(MetricLogger(counter, logLevelLabel))

  def makeMetricLogger(counter: Metric.Counter[Long], logLevelLabel: String): ZIO[Any, Nothing, MetricLogger] =
    ZIO.succeed(MetricLogger(counter, logLevelLabel))

  implicit final class ZLoggerLayerLayerOps[-RIn, +E, ROut <: ZLogger[String, Any]: Tag](
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

  implicit final class ZLoggerZIOLayerOps[-RIn, +E, ROut <: ZLogger[String, Any]: Tag](
    private val self: ZIO[RIn, E, ROut]
  ) {

    def install: ZLayer[RIn, E, Unit] =
      ZLayer.scoped[RIn] {
        self.flatMap { logger =>
          ZIO.withLoggerScoped(logger)
        }
      }

    def installScoped: ZLayer[Scope with RIn, E, Unit] =
      ZLayer.fromZIO(self.flatMap { logger =>
        ZIO.withLoggerScoped(logger)
      })

  }

}
