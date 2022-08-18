/*
 * Copyright 2019-2022 John A. De Goes and the ZIO Contributors
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

  def console(
    format: LogFormat = LogFormat.colored,
    logLevel: LogLevel = LogLevel.Info
  ): ZLayer[Any, Nothing, Unit] =
    makeConsole(format.toLogger, java.lang.System.out, logLevel)

  def consoleJson(
    format: LogFormat = LogFormat.default,
    logLevel: LogLevel = LogLevel.Info
  ): ZLayer[Any, Nothing, Unit] =
    makeConsole(format.toJsonLogger, java.lang.System.out, logLevel)

  def consoleErr(
    format: LogFormat = LogFormat.default,
    logLevel: LogLevel = LogLevel.Info
  ): ZLayer[Any, Nothing, Unit] =
    makeConsole(format.toLogger, java.lang.System.err, logLevel)

  def consoleErrJson(
    format: LogFormat = LogFormat.default,
    logLevel: LogLevel = LogLevel.Info
  ): ZLayer[Any, Nothing, Unit] =
    makeConsole(format.toJsonLogger, java.lang.System.err, logLevel)

  def file(
    destination: Path,
    format: LogFormat = LogFormat.default,
    logLevel: LogLevel = LogLevel.Info,
    charset: Charset = StandardCharsets.UTF_8,
    autoFlushBatchSize: Int = 1,
    bufferedIOSize: Option[Int] = None
  ): ZLayer[Any, Nothing, Unit] =
    Runtime.addLogger(
      makeStringLogger(destination, format.toLogger, logLevel, charset, autoFlushBatchSize, bufferedIOSize)
    )

  def fileJson(
    destination: Path,
    format: LogFormat = LogFormat.default,
    logLevel: LogLevel = LogLevel.Info,
    charset: Charset = StandardCharsets.UTF_8,
    autoFlushBatchSize: Int = 1,
    bufferedIOSize: Option[Int] = None
  ): ZLayer[Any, Nothing, Unit] =
    Runtime.addLogger(
      makeStringLogger(destination, format.toJsonLogger, logLevel, charset, autoFlushBatchSize, bufferedIOSize)
    )

  def fileAsync(
    destination: Path,
    format: LogFormat = LogFormat.default,
    logLevel: LogLevel = LogLevel.Info,
    charset: Charset = StandardCharsets.UTF_8,
    autoFlushBatchSize: Int = 1,
    bufferedIOSize: Option[Int] = None
  ): ZLayer[Any, Nothing, Unit] =
    makeFileAsync(destination, format.toLogger, logLevel, charset, autoFlushBatchSize, bufferedIOSize)

  def fileAsyncJson(
    destination: Path,
    format: LogFormat = LogFormat.default,
    logLevel: LogLevel = LogLevel.Info,
    charset: Charset = StandardCharsets.UTF_8,
    autoFlushBatchSize: Int = 1,
    bufferedIOSize: Option[Int] = None
  ): ZLayer[Any, Nothing, Unit] =
    makeFileAsync(destination, format.toJsonLogger, logLevel, charset, autoFlushBatchSize, bufferedIOSize)

  val removeDefaultLoggers: ZLayer[Any, Nothing, Unit] = Runtime.removeDefaultLoggers

  private def makeConsole(
    logger: ZLogger[String, String],
    stream: PrintStream,
    logLevel: LogLevel
  ): ZLayer[Any, Nothing, Unit] = {
    val stringLogger = logger.map { line =>
      try stream.println(line)
      catch {
        case t: VirtualMachineError => throw t
        case _: Throwable           => ()
      }
    }.filterLogLevel(_ >= logLevel)

    Runtime.addLogger(stringLogger)
  }

  private def makeStringLogger(
    destination: Path,
    logger: ZLogger[String, String],
    logLevel: LogLevel,
    charset: Charset,
    autoFlushBatchSize: Int,
    bufferedIOSize: Option[Int]
  ): ZLogger[String, Any] = {
    val logWriter = new internal.FileWriter(destination, charset, autoFlushBatchSize, bufferedIOSize)

    val stringLogger: ZLogger[String, Any] =
      logger.map { (line: String) =>
        try logWriter.writeln(line)
        catch {
          case t: VirtualMachineError => throw t
          case _: Throwable           => ()
        }
      }.filterLogLevel(_ >= logLevel)

    stringLogger
  }

  private def makeFileAsync(
    destination: Path,
    logger: ZLogger[String, String],
    logLevel: LogLevel,
    charset: Charset,
    autoFlushBatchSize: Int,
    bufferedIOSize: Option[Int]
  ): ZLayer[Any, Nothing, Unit] =
    ZLayer.scoped {
      for {
        queue       <- Queue.bounded[UIO[Any]](1000)
        stringLogger =
          makeAsyncStringLogger(destination, logger, logLevel, charset, autoFlushBatchSize, bufferedIOSize, queue)
        _           <- FiberRef.currentLoggers.locallyScopedWith(_ + stringLogger)
        _           <- queue.take.flatMap(task => task.ignore).forever.forkScoped
      } yield ()
    }

  private def makeAsyncStringLogger(
    destination: Path,
    logger: ZLogger[String, String],
    logLevel: LogLevel,
    charset: Charset,
    autoFlushBatchSize: Int,
    bufferedIOSize: Option[Int],
    queue: Queue[UIO[Any]]
  ): ZLogger[String, Any] = {
    val logWriter = new internal.FileWriter(destination, charset, autoFlushBatchSize, bufferedIOSize)

    val stringLogger: ZLogger[String, Any] =
      logger.map { (line: String) =>
        zio.Unsafe.unsafe { implicit u =>
          Runtime.default.unsafe.run(queue.offer(ZIO.succeed {
            try logWriter.writeln(line)
            catch {
              case t: VirtualMachineError => throw t
              case _: Throwable           => ()
            }
          }))
        }
      }.filterLogLevel(_ >= logLevel)
    stringLogger
  }
}
