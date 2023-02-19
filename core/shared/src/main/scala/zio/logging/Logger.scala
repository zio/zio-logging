package zio.logging

import zio._
import zio.logging.internal.LogPattern
import zio.metrics.{ Metric, MetricLabel }

import java.io.PrintStream
import java.net.URI
import java.nio.charset.{ Charset, StandardCharsets }
import java.nio.file.{ Path, Paths }
import scala.util.{ Failure, Success, Try }

object Logger {

  final case class ConsoleLoggerConfig(format: LogFormat, filter: LogFilter[String])

  object ConsoleLoggerConfig {
    def apply(pattern: LogPattern, filter: LogFilter[String]): ConsoleLoggerConfig =
      ConsoleLoggerConfig(pattern.toLogFormat, filter)

    val config: Config[ConsoleLoggerConfig] = {
      val patternConfig = LogPattern.config.nested("pattern")
      val filterConfig  = LogFilter.LogLevelByNameConfig.config.nested("filter")
      (patternConfig ++ filterConfig).map { case (pattern, filterConfig) =>
        ConsoleLoggerConfig(pattern, LogFilter.logLevelByName(filterConfig))
      }
    }
  }

  final case class FileLoggerConfig(
    destination: Path,
    format: LogFormat,
    filter: LogFilter[String],
    charset: Charset,
    autoFlushBatchSize: Int,
    bufferedIOSize: Option[Int]
  )

  object FileLoggerConfig {
    def apply(
      destination: Path,
      pattern: LogPattern,
      filter: LogFilter[String],
      charset: Charset,
      autoFlushBatchSize: Int,
      bufferedIOSize: Option[Int]
    ): FileLoggerConfig =
      FileLoggerConfig(destination, pattern.toLogFormat, filter, charset, autoFlushBatchSize, bufferedIOSize)

    val config: Config[FileLoggerConfig] = {

      def pathValue(value: String): Either[Config.Error.InvalidData, Path] =
        Try(Paths.get(URI.create(value))) match {
          case Success(v) => Right(v)
          case Failure(_) =>
            Left(Config.Error.InvalidData(Chunk.empty, s"Expected a Path, but found ${value}"))
        }

      def charsetValue(value: String): Either[Config.Error.InvalidData, Charset] =
        Try(Charset.forName(value)) match {
          case Success(v) => Right(v)
          case Failure(_) =>
            Left(Config.Error.InvalidData(Chunk.empty, s"Expected a Charset, but found ${value}"))
        }

      val pathConfig               = Config.string.mapOrFail(pathValue).nested("path")
      val patternConfig            = LogPattern.config.nested("pattern")
      val filterConfig             = LogFilter.LogLevelByNameConfig.config.nested("filter")
      val charsetConfig            = Config.string.mapOrFail(charsetValue).nested("charset").withDefault(StandardCharsets.UTF_8)
      val autoFlushBatchSizeConfig = Config.int.nested("autoFlushBatchSize").withDefault(1)
      val bufferedIOSizeConfig     = Config.int.nested("bufferedIOSize").optional

      (pathConfig ++ patternConfig ++ filterConfig ++ charsetConfig ++ autoFlushBatchSizeConfig ++ bufferedIOSizeConfig).map {
        case (path, pattern, filterConfig, charset, autoFlushBatchSize, bufferedIOSize) =>
          FileLoggerConfig(
            path,
            pattern,
            LogFilter.logLevelByName(filterConfig),
            charset,
            autoFlushBatchSize,
            bufferedIOSize
          )
      }
    }
  }

  def consoleLogger(configPath: String = "logger"): ZLayer[Any, Config.Error, Unit] =
    ZLayer.scoped {
      for {
        config <- ZIO.config(ConsoleLoggerConfig.config.nested(configPath))
        _      <- ZIO.withLoggerScoped(makeConsoleLogger(config))
      } yield ()
    }

  def makeConsoleLogger(config: ConsoleLoggerConfig): ZLogger[String, Any] =
    makeConsoleLogger(config.format.toLogger, java.lang.System.out, config.filter)

  def makeConsoleLogger(
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

  def fileStringLogger(configPath: String = "logger"): ZLayer[Any, Config.Error, Unit] =
    ZLayer.scoped {
      for {
        config <- ZIO.config(FileLoggerConfig.config.nested(configPath))
        _      <- ZIO.withLoggerScoped(makeFileStringLogger(config))
      } yield ()
    }

  def makeFileStringLogger(config: FileLoggerConfig): ZLogger[String, Any] =
    makeFileStringLogger(
      config.destination,
      config.format.toLogger,
      config.filter,
      config.charset,
      config.autoFlushBatchSize,
      config.bufferedIOSize
    )

  def makeFileStringLogger(
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

  def makeFileAsync(
    destination: Path,
    logger: ZLogger[String, String],
    logFilter: LogFilter[String],
    charset: Charset,
    autoFlushBatchSize: Int,
    bufferedIOSize: Option[Int]
  ): ZLayer[Any, Nothing, Unit] =
    ZLayer.scoped {
      for {
        queue       <- Queue.bounded[UIO[Any]](1000)
        stringLogger =
          makeFileAsyncStringLogger(destination, logger, logFilter, charset, autoFlushBatchSize, bufferedIOSize, queue)
        _           <- ZIO.withLoggerScoped(stringLogger)
        _           <- queue.take.flatMap(task => task.ignore).forever.forkScoped
      } yield ()
    }

  def makeFileAsyncStringLogger(
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

  def makeMetricLogger(counter: Metric.Counter[Long], logLevelLabel: String): ZLogger[String, Unit] =
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

}
