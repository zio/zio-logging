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
    FiberRef.unsafeMake(LogContext.empty, identity, (old, newV) => old ++ newV)

  def console(
    format: LogFormat = LogFormat.colored,
    logLevel: LogLevel = LogLevel.Info
  ): ZLayer[Any, Nothing, Unit] = {
    val stringLogger = format.toLogger.map { line =>
      try java.lang.System.out.println(line)
      catch {
        case t: VirtualMachineError => throw t
        case _: Throwable           => ()
      }
    }.filterLogLevel(_ >= logLevel)

    Runtime.addLogger(stringLogger)
  }

  def consoleErr(
    format: LogFormat = LogFormat.default,
    logLevel: LogLevel = LogLevel.Info
  ): ZLayer[Any, Nothing, Unit] = {
    val stringLogger = format.toLogger.map { line =>
      try java.lang.System.err.println(line)
      catch {
        case t: VirtualMachineError => throw t
        case _: Throwable           => ()
      }
    }.filterLogLevel(_ >= logLevel)

    Runtime.addLogger(stringLogger)
  }

  def file(
    destination: Path,
    format: LogFormat = LogFormat.default,
    logLevel: LogLevel = LogLevel.Info,
    charset: Charset = StandardCharsets.UTF_8,
    autoFlushBatchSize: Int = 1,
    bufferedIOSize: Option[Int] = None
  ): ZLayer[Any, Nothing, Unit] =
    Runtime.addLogger(
      makeStringLogger(destination, format, logLevel, charset, autoFlushBatchSize, bufferedIOSize)
    )

  def fileAsync(
    destination: Path,
    format: LogFormat = LogFormat.default,
    logLevel: LogLevel = LogLevel.Info,
    charset: Charset = StandardCharsets.UTF_8,
    autoFlushBatchSize: Int = 1,
    bufferedIOSize: Option[Int] = None
  ): ZLayer[Any, Nothing, Unit] =
    ZLayer.scoped {
      for {
        queue       <- Queue.bounded[UIO[Any]](1000)
        stringLogger =
          makeAsyncStringLogger(destination, format, logLevel, charset, autoFlushBatchSize, bufferedIOSize, queue)
        _           <- FiberRef.currentLoggers.locallyScopedWith(_ + stringLogger)
        _           <- queue.take.flatMap(task => task.ignore).forever.forkScoped
      } yield ()
    }

  val removeDefaultLoggers: ZLayer[Any, Nothing, Unit] = {
    implicit val trace = Trace.empty
    ZLayer.scoped(FiberRef.currentLoggers.locallyScopedWith(_ -- Runtime.defaultLoggers))
  }

  private def makeStringLogger(
    destination: Path,
    format: LogFormat,
    logLevel: LogLevel,
    charset: Charset,
    autoFlushBatchSize: Int,
    bufferedIOSize: Option[Int]
  ): ZLogger[String, Any] = {
    val logWriter = new internal.FileWriter(destination, charset, autoFlushBatchSize, bufferedIOSize)

    val stringLogger: ZLogger[String, Any] =
      format.toLogger.map { (line: String) =>
        try logWriter.append(line)
        catch {
          case t: VirtualMachineError => throw t
          case _: Throwable           => ()
        }
      }.filterLogLevel(_ >= logLevel)

    stringLogger
  }

  private def makeAsyncStringLogger(
    destination: Path,
    format: LogFormat,
    logLevel: LogLevel,
    charset: Charset,
    autoFlushBatchSize: Int,
    bufferedIOSize: Option[Int],
    queue: Queue[UIO[Any]]
  ): ZLogger[String, Any] = {
    val logWriter = new internal.FileWriter(destination, charset, autoFlushBatchSize, bufferedIOSize)

    val stringLogger: ZLogger[String, Any] =
      format.toLogger.map { (line: String) =>
        Runtime.default.unsafeRun(queue.offer(ZIO.succeed {
          try logWriter.append(line)
          catch {
            case t: VirtualMachineError => throw t
            case _: Throwable           => ()
          }
        }))
      }.filterLogLevel(_ >= logLevel)

    stringLogger
  }
}
