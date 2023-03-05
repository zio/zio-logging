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
package zio

import zio.metrics.{ Metric, MetricLabel }

import java.io.PrintStream
import java.nio.charset.{ Charset, StandardCharsets }
import java.nio.file.Path

package object logging {

  /**
   * The [[logContext]] fiber reference is used to store typed, structured log
   * annotations, which can be utilized by backends to enrich log messages.
   *
   * Because [[logContext]] is an ordinary [[zio.FiberRef]], it may be get, set,
   * and updated like any other fiber reference. However, the idiomatic way to
   * interact with [[logContext]] is by using [[zio.logging.LogAnnotation]].
   *
   * For example:
   *
   * {{{
   * myResponseHandler(request) @@ UserId(request.userId)
   * }}}
   *
   * This code would add the structured log annotation [[LogAnnotation.UserId]]
   * to all log messages emitted by the `myResponseHandler(request)` effect.
   */
  val logContext: FiberRef[LogContext] =
    zio.Unsafe.unsafe { implicit u =>
      FiberRef.unsafe.make(LogContext.empty, ZIO.identityFn[LogContext], (old, newV) => old ++ newV)
    }

  /**
   * log aspect annotation key for logger name
   */
  val loggerNameAnnotationKey = "logger_name"

  private[logging] val logLevelMetricLabel = "level"

  private[logging] val loggedTotalMetric = Metric.counter(name = "zio_log_total")

  /**
   * Logger name aspect, by this aspect is possible to set logger name (in general, logger name is extracted from [[Trace]])
   *
   * annotation key: [[zio.logging.loggerNameAnnotationKey]]
   */
  def loggerName(value: String): ZIOAspect[Nothing, Any, Nothing, Any, Nothing, Any] =
    ZIOAspect.annotated(loggerNameAnnotationKey, value)

  @deprecated("use zio.logging.consoleLogger", "2.1.10")
  def console(
    format: LogFormat = LogFormat.colored,
    logLevel: LogLevel = LogLevel.Info
  ): ZLayer[Any, Nothing, Unit] =
    console(format, LogFilter.logLevel(logLevel))

  @deprecated("use zio.logging.consoleLogger", "2.1.10")
  def console(
    format: LogFormat,
    logFilter: LogFilter[String]
  ): ZLayer[Any, Nothing, Unit] =
    consoleLogger(ConsoleLoggerConfig(format, logFilter))

  @deprecated("use zio.logging.consoleErrLogger", "2.1.10")
  def consoleErr(
    format: LogFormat = LogFormat.default,
    logLevel: LogLevel = LogLevel.Info
  ): ZLayer[Any, Nothing, Unit] =
    consoleErr(format, LogFilter.logLevel(logLevel))

  @deprecated("use zio.logging.consoleErrLogger", "2.1.10")
  def consoleErr(
    format: LogFormat,
    logFilter: LogFilter[String]
  ): ZLayer[Any, Nothing, Unit] =
    consoleErrLogger(ConsoleLoggerConfig(format, logFilter))

  @deprecated("use zio.logging.consoleErrJsonLogger", "2.1.10")
  def consoleErrJson(
    format: LogFormat = LogFormat.default,
    logLevel: LogLevel = LogLevel.Info
  ): ZLayer[Any, Nothing, Unit] =
    consoleErrJson(format, LogFilter.logLevel(logLevel))

  @deprecated("use zio.logging.consoleErrJsonLogger", "2.1.10")
  def consoleErrJson(
    format: LogFormat,
    logFilter: LogFilter[String]
  ): ZLayer[Any, Nothing, Unit] =
    consoleErrJsonLogger(ConsoleLoggerConfig(format, logFilter))

  def consoleErrLogger(config: ConsoleLoggerConfig): ZLayer[Any, Nothing, Unit] =
    Runtime.addLogger(makeConsoleErrLogger(config))

  def consoleErrJsonLogger(config: ConsoleLoggerConfig): ZLayer[Any, Nothing, Unit] =
    Runtime.addLogger(makeConsoleErrJsonLogger(config))

  def consoleErrJsonLogger(configPath: String = "logger"): ZLayer[Any, Config.Error, Unit] =
    ZLayer.scoped {
      for {
        config <- ZIO.config(ConsoleLoggerConfig.config.nested(configPath))
        _      <- ZIO.withLoggerScoped(makeConsoleErrJsonLogger(config))
      } yield ()
    }

  def consoleErrLogger(configPath: String = "logger"): ZLayer[Any, Config.Error, Unit] =
    ZLayer.scoped {
      for {
        config <- ZIO.config(ConsoleLoggerConfig.config.nested(configPath))
        _      <- ZIO.withLoggerScoped(makeConsoleErrLogger(config))
      } yield ()
    }

  @deprecated("use zio.logging.consoleJsonLogger", "2.1.10")
  def consoleJson(
    format: LogFormat = LogFormat.default,
    logLevel: LogLevel = LogLevel.Info
  ): ZLayer[Any, Nothing, Unit] =
    consoleJson(format, LogFilter.logLevel(logLevel))

  @deprecated("use zio.logging.consoleJsonLogger", "2.1.10")
  def consoleJson(
    format: LogFormat,
    logFilter: LogFilter[String]
  ): ZLayer[Any, Nothing, Unit] =
    consoleJsonLogger(ConsoleLoggerConfig(format, logFilter))

  def consoleJsonLogger(config: ConsoleLoggerConfig): ZLayer[Any, Nothing, Unit] =
    Runtime.addLogger(makeConsoleJsonLogger(config))

  def consoleJsonLogger(configPath: String = "logger"): ZLayer[Any, Config.Error, Unit] =
    ZLayer.scoped {
      for {
        config <- ZIO.config(ConsoleLoggerConfig.config.nested(configPath))
        _      <- ZIO.withLoggerScoped(makeConsoleJsonLogger(config))
      } yield ()
    }

  def consoleLogger(config: ConsoleLoggerConfig): ZLayer[Any, Nothing, Unit] =
    Runtime.addLogger(makeConsoleLogger(config))

  def consoleLogger(configPath: String = "logger"): ZLayer[Any, Config.Error, Unit] =
    ZLayer.scoped {
      for {
        config <- ZIO.config(ConsoleLoggerConfig.config.nested(configPath))
        _      <- ZIO.withLoggerScoped(makeConsoleLogger(config))
      } yield ()
    }

  @deprecated("use zio.logging.fileLogger", "2.1.10")
  def file(
    destination: Path,
    format: LogFormat = LogFormat.default,
    logLevel: LogLevel = LogLevel.Info,
    charset: Charset = StandardCharsets.UTF_8,
    autoFlushBatchSize: Int = 1,
    bufferedIOSize: Option[Int] = None
  ): ZLayer[Any, Nothing, Unit] =
    file(destination, format, LogFilter.logLevel(logLevel), charset, autoFlushBatchSize, bufferedIOSize)

  @deprecated("use zio.logging.fileLogger", "2.1.10")
  def file(
    destination: Path,
    format: LogFormat,
    logFilter: LogFilter[String],
    charset: Charset,
    autoFlushBatchSize: Int,
    bufferedIOSize: Option[Int]
  ): ZLayer[Any, Nothing, Unit] =
    fileLogger(FileLoggerConfig(destination, format, logFilter, charset, autoFlushBatchSize, bufferedIOSize))

  @deprecated("use zio.logging.fileAsyncLogger", "2.1.10")
  def fileAsync(
    destination: Path,
    format: LogFormat = LogFormat.default,
    logLevel: LogLevel = LogLevel.Info,
    charset: Charset = StandardCharsets.UTF_8,
    autoFlushBatchSize: Int = 1,
    bufferedIOSize: Option[Int] = None
  ): ZLayer[Any, Nothing, Unit] =
    fileAsync(
      destination,
      format,
      LogFilter.logLevel(logLevel),
      charset,
      autoFlushBatchSize,
      bufferedIOSize
    )

  @deprecated("use zio.logging.fileAsyncLogger", "2.1.10")
  def fileAsync(
    destination: Path,
    format: LogFormat,
    logFilter: LogFilter[String],
    charset: Charset,
    autoFlushBatchSize: Int,
    bufferedIOSize: Option[Int]
  ): ZLayer[Any, Nothing, Unit] =
    fileAsyncLogger(FileLoggerConfig(destination, format, logFilter, charset, autoFlushBatchSize, bufferedIOSize))

  @deprecated("use zio.logging.fileJsonLogger", "2.1.10")
  def fileJson(
    destination: Path,
    format: LogFormat = LogFormat.default,
    logLevel: LogLevel = LogLevel.Info,
    charset: Charset = StandardCharsets.UTF_8,
    autoFlushBatchSize: Int = 1,
    bufferedIOSize: Option[Int] = None
  ): ZLayer[Any, Nothing, Unit] =
    fileJson(destination, format, LogFilter.logLevel(logLevel), charset, autoFlushBatchSize, bufferedIOSize)

  @deprecated("use zio.logging.fileJsonLogger", "2.1.10")
  def fileJson(
    destination: Path,
    format: LogFormat,
    logFilter: LogFilter[String],
    charset: Charset,
    autoFlushBatchSize: Int,
    bufferedIOSize: Option[Int]
  ): ZLayer[Any, Nothing, Unit] =
    fileJsonLogger(
      FileLoggerConfig(destination, format, logFilter, charset, autoFlushBatchSize, bufferedIOSize)
    )

  @deprecated("use zio.logging.fileAsyncJsonLogger", "2.1.10")
  def fileAsyncJson(
    destination: Path,
    format: LogFormat = LogFormat.default,
    logLevel: LogLevel = LogLevel.Info,
    charset: Charset = StandardCharsets.UTF_8,
    autoFlushBatchSize: Int = 1,
    bufferedIOSize: Option[Int] = None
  ): ZLayer[Any, Nothing, Unit] =
    fileAsyncJson(
      destination,
      format,
      LogFilter.logLevel(logLevel),
      charset,
      autoFlushBatchSize,
      bufferedIOSize
    )

  @deprecated("use zio.logging.fileAsyncJsonLogger", "2.1.10")
  def fileAsyncJson(
    destination: Path,
    format: LogFormat,
    logFilter: LogFilter[String],
    charset: Charset,
    autoFlushBatchSize: Int,
    bufferedIOSize: Option[Int]
  ): ZLayer[Any, Nothing, Unit] =
    fileAsyncJsonLogger(
      FileLoggerConfig(destination, format, logFilter, charset, autoFlushBatchSize, bufferedIOSize)
    )

  def fileAsyncJsonLogger(config: FileLoggerConfig): ZLayer[Any, Nothing, Unit] =
    ZLayer.scoped(makeFileAsyncJsonLogger(config))

  def fileAsyncJsonLogger(configPath: String = "logger"): ZLayer[Any, Config.Error, Unit] =
    ZLayer.scoped {
      for {
        config <- ZIO.config(FileLoggerConfig.config.nested(configPath))
        _      <- makeFileAsyncJsonLogger(config)
      } yield ()
    }

  def fileAsyncLogger(config: FileLoggerConfig): ZLayer[Any, Nothing, Unit] =
    ZLayer.scoped(makeFileAsyncLogger(config))

  def fileAsyncLogger(configPath: String = "logger"): ZLayer[Any, Config.Error, Unit] =
    ZLayer.scoped {
      for {
        config <- ZIO.config(FileLoggerConfig.config.nested(configPath))
        _      <- makeFileAsyncLogger(config)
      } yield ()
    }

  def fileJsonLogger(config: FileLoggerConfig): ZLayer[Any, Nothing, Unit] =
    Runtime.addLogger(makeFileJsonLogger(config))

  def fileJsonLogger(configPath: String = "logger"): ZLayer[Any, Config.Error, Unit] =
    ZLayer.scoped {
      for {
        config <- ZIO.config(FileLoggerConfig.config.nested(configPath))
        _      <- ZIO.withLoggerScoped(makeFileJsonLogger(config))
      } yield ()
    }

  def fileLogger(config: FileLoggerConfig): ZLayer[Any, Nothing, Unit] =
    Runtime.addLogger(makeFileLogger(config))

  def fileLogger(configPath: String = "logger"): ZLayer[Any, Config.Error, Unit] =
    ZLayer.scoped {
      for {
        config <- ZIO.config(FileLoggerConfig.config.nested(configPath))
        _      <- ZIO.withLoggerScoped(makeFileLogger(config))
      } yield ()
    }

  val logMetrics: ZLayer[Any, Nothing, Unit] =
    Runtime.addLogger(makeMetricLogger(loggedTotalMetric, logLevelMetricLabel))

  def logMetricsWith(name: String, logLevelLabel: String): ZLayer[Any, Nothing, Unit] =
    Runtime.addLogger(makeMetricLogger(Metric.counter(name), logLevelLabel))

  private def makeConsoleErrLogger(config: ConsoleLoggerConfig): ZLogger[String, Any] =
    makeConsoleLogger(config.format.toLogger, java.lang.System.err, config.filter)

  private def makeConsoleErrJsonLogger(config: ConsoleLoggerConfig): ZLogger[String, Any] =
    makeConsoleLogger(config.format.toJsonLogger, java.lang.System.err, config.filter)

  private def makeConsoleLogger(config: ConsoleLoggerConfig): ZLogger[String, Any] =
    makeConsoleLogger(config.format.toLogger, java.lang.System.out, config.filter)

  private def makeConsoleJsonLogger(config: ConsoleLoggerConfig): ZLogger[String, Any] =
    makeConsoleLogger(config.format.toJsonLogger, java.lang.System.out, config.filter)

  private def makeConsoleLogger(
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

  private def makeFileAsyncJsonLogger(
    config: FileLoggerConfig
  ): ZIO[Scope, Nothing, Unit] = makeFileAsyncLogger(
    config.destination,
    config.format.toJsonLogger,
    config.filter,
    config.charset,
    config.autoFlushBatchSize,
    config.bufferedIOSize
  )

  private def makeFileAsyncLogger(
    config: FileLoggerConfig
  ): ZIO[Scope, Nothing, Unit] = makeFileAsyncLogger(
    config.destination,
    config.format.toLogger,
    config.filter,
    config.charset,
    config.autoFlushBatchSize,
    config.bufferedIOSize
  )

  private def makeFileAsyncLogger(
    destination: Path,
    logger: ZLogger[String, String],
    logFilter: LogFilter[String],
    charset: Charset,
    autoFlushBatchSize: Int,
    bufferedIOSize: Option[Int]
  ): ZIO[Scope, Nothing, Unit] =
    for {
      queue       <- Queue.bounded[UIO[Any]](1000)
      stringLogger =
        makeFileAsyncLogger(destination, logger, logFilter, charset, autoFlushBatchSize, bufferedIOSize, queue)
      _           <- ZIO.withLoggerScoped(stringLogger)
      _           <- queue.take.flatMap(task => task.ignore).forever.forkScoped
    } yield ()

  private def makeFileAsyncLogger(
    destination: Path,
    logger: ZLogger[String, String],
    logFilter: LogFilter[String],
    charset: Charset,
    autoFlushBatchSize: Int,
    bufferedIOSize: Option[Int],
    queue: Queue[UIO[Any]]
  ): ZLogger[String, Any] = {
    val logWriter = new zio.logging.internal.FileWriter(destination, charset, autoFlushBatchSize, bufferedIOSize)

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

  private def makeFileJsonLogger(config: FileLoggerConfig): ZLogger[String, Any] =
    makeFileLogger(
      config.destination,
      config.format.toJsonLogger,
      config.filter,
      config.charset,
      config.autoFlushBatchSize,
      config.bufferedIOSize
    )

  private def makeFileLogger(config: FileLoggerConfig): ZLogger[String, Any] =
    makeFileLogger(
      config.destination,
      config.format.toLogger,
      config.filter,
      config.charset,
      config.autoFlushBatchSize,
      config.bufferedIOSize
    )

  private def makeFileLogger(
    destination: Path,
    logger: ZLogger[String, String],
    logFilter: LogFilter[String],
    charset: Charset,
    autoFlushBatchSize: Int,
    bufferedIOSize: Option[Int]
  ): ZLogger[String, Any] = {
    val logWriter = new zio.logging.internal.FileWriter(destination, charset, autoFlushBatchSize, bufferedIOSize)

    val stringLogger: ZLogger[String, Any] = logFilter.filter(logger.map { (line: String) =>
      try logWriter.writeln(line)
      catch {
        case t: VirtualMachineError => throw t
        case _: Throwable           => ()
      }
    })

    stringLogger
  }

  private def makeMetricLogger(counter: Metric.Counter[Long], logLevelLabel: String): ZLogger[String, Unit] =
    new ZLogger[String, Unit] {
      override def apply(
        trace: Trace,
        fiberId: FiberId,
        logLevel: LogLevel,
        message: () => String,
        cause: Cause[Any],
        context: FiberRefs,
        spans: List[LogSpan],
        annotations: Map[String, String]
      ): Unit = {
        val tags = context.get(FiberRef.currentTags).getOrElse(Set.empty)
        counter.unsafe.update(1, tags + MetricLabel(logLevelLabel, logLevel.label.toLowerCase))(Unsafe.unsafe)
        ()
      }
    }

  val removeDefaultLoggers: ZLayer[Any, Nothing, Unit] = Runtime.removeDefaultLoggers

}
