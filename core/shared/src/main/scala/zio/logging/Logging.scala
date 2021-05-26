package zio.logging

import zio._
import zio.clock._
import zio.console.Console
import zio.logging.Logger.LoggerWithFormat
import zio.stream.ZStream

import java.nio.charset.{ Charset, StandardCharsets }
import java.nio.file.Path

object Logging {

  /**
   * Add timestamp annotation to every message
   */
  def addTimestamp[A](self: Logger[A]): URIO[Clock, Logger[A]] =
    self.deriveM(ctx => currentDateTime.orDie.map(time => LogAnnotation.Timestamp(time)(ctx)))

  val any: ZLayer[Logging, Nothing, Logging] = ZLayer.requires[Logging]

  def console(
    logLevel: LogLevel = LogLevel.Info,
    format: LogFormat[String] = LogFormat.ColoredLogFormat((_, s) => s)
  ): ZLayer[Console with Clock, Nothing, Logging] =
    ZLayer.requires[Clock] ++
      LogAppender.console[String](
        logLevel,
        format
      ) >+> make >>> modifyLoggerM(addTimestamp[String])

  def consoleErr(
    logLevel: LogLevel = LogLevel.Info,
    format: LogFormat[String] = LogFormat.SimpleConsoleLogFormat((_, s) => s)
  ): ZLayer[Console with Clock, Nothing, Logging] =
    ZLayer.requires[Clock] ++
      LogAppender.consoleErr[String](
        logLevel,
        format
      ) >+> make >>> modifyLoggerM(addTimestamp[String])

  val context: URIO[Logging, LogContext] =
    ZIO.accessM[Logging](_.get.logContext)

  def debug(line: => String): ZIO[Logging, Nothing, Unit] =
    ZIO.accessM[Logging](_.get.debug(line))

  def derive(f: LogContext => LogContext): ZIO[Logging, Nothing, Logger[String]] =
    ZIO.access[Logging](_.get.derive(f))

  def error(line: => String): ZIO[Logging, Nothing, Unit] =
    ZIO.accessM[Logging](_.get.error(line))

  def error(line: => String, cause: Cause[Any]): ZIO[Logging, Nothing, Unit] =
    ZIO.accessM[Logging](_.get.error(line, cause))

  def file(
    destination: Path,
    charset: Charset = StandardCharsets.UTF_8,
    autoFlushBatchSize: Int = 1,
    bufferedIOSize: Option[Int] = None,
    logLevel: LogLevel = LogLevel.Info,
    format: LogFormat[String] = LogFormat.SimpleConsoleLogFormat((_, s) => s)
  ): ZLayer[Console with Clock, Throwable, Logging] =
    (ZLayer.requires[Clock] ++
      LogAppender
        .file[String](destination, charset, autoFlushBatchSize, bufferedIOSize, format)
        .map(appender => Has(appender.get.filter((ctx, _) => ctx.get(LogAnnotation.Level) >= logLevel)))
      >+> Logging.make >>> modifyLoggerM(addTimestamp[String]))

  def fileAsync(
    destination: Path,
    charset: Charset = StandardCharsets.UTF_8,
    autoFlushBatchSize: Int = 32,
    bufferedIOSize: Option[Int] = Some(8192),
    logLevel: LogLevel = LogLevel.Info,
    format: LogFormat[String] = LogFormat.SimpleConsoleLogFormat((_, s) => s)
  ): ZLayer[Console with Clock, Throwable, Logging] =
    (ZLayer.requires[Clock] ++
      (LogAppender
        .file[String](destination, charset, autoFlushBatchSize, bufferedIOSize, format)
        .map(appender => Has(appender.get.filter((ctx, _) => ctx.get(LogAnnotation.Level) >= logLevel)))
        >>> LogAppender.async(autoFlushBatchSize))
      >+> Logging.make >>> modifyLoggerM(addTimestamp[String]))

  val ignore: Layer[Nothing, Logging] =
    LogAppender.ignore[String] >>> make

  def info(line: => String): ZIO[Logging, Nothing, Unit] =
    ZIO.accessM[Logging](_.get.info(line))

  def log(level: LogLevel)(line: => String): ZIO[Logging, Nothing, Unit] =
    ZIO.accessM[Logging](_.get.log(level)(line))

  def locally[A, R <: Logging, E, A1](fn: LogContext => LogContext)(zio: ZIO[R, E, A1]): ZIO[Logging with R, E, A1] =
    ZIO.accessM(_.get.locally(fn)(zio))

  def locallyM[A, R <: Logging, E, A1](
    fn: LogContext => URIO[R, LogContext]
  )(zio: ZIO[R, E, A1]): ZIO[Logging with R, E, A1] =
    ZIO.accessM(_.get.locallyM(fn)(zio))

  def locallyManaged[A, R <: Logging, E, A1](fn: LogContext => LogContext)(
    zio: ZManaged[R, E, A1]
  ): ZManaged[Logging with R, E, A1] =
    ZManaged.accessManaged(_.get.locallyManaged(fn)(zio))

  def locallyZStream[A, R <: Logging, E, A1](fn: LogContext => LogContext)(
    stream: ZStream[R, E, A1]
  ): ZStream[Logging with R, E, A1] =
    ZStream.accessStream(_.get.locallyZStream(fn)(stream))

  def make: URLayer[Appender[String], Logging] =
    ZLayer.fromFunctionM((appender: Appender[String]) =>
      FiberRef
        .make(LogContext.empty)
        .map { ref =>
          LoggerWithFormat(ref, appender.get)
        }
    )

  def modifyLogger(fn: Logger[String] => Logger[String]): ZLayer[Logging, Nothing, Logging] =
    ZLayer.fromFunction[Logging, Logger[String]](logging => fn(logging.get))

  def modifyLoggerM[R, E](fn: Logger[String] => ZIO[R, E, Logger[String]]): ZLayer[Logging with R, E, Logging] =
    ZLayer.fromFunctionM[Logging with R, E, Logger[String]](logging => fn(logging.get).provide(logging))

  def throwable(line: => String, t: Throwable): ZIO[Logging, Nothing, Unit] =
    ZIO.accessM[Logging](_.get.throwable(line, t))

  def trace(line: => String): ZIO[Logging, Nothing, Unit] =
    ZIO.accessM[Logging](_.get.trace(line))

  def warn(line: => String): ZIO[Logging, Nothing, Unit] =
    ZIO.accessM[Logging](_.get.warn(line))

  /**
   * Adds root logger name
   */
  def withRootLoggerName(name: String): ZLayer[Logging, Nothing, Logging] =
    modifyLogger(_.named(name))

  /**
   * modify initial context
   */
  def withContext(context: LogContext): ZLayer[Logging, Nothing, Logging] =
    modifyLogger(_.derive(_ => context))
}
