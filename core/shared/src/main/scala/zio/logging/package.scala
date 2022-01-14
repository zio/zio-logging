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

  // TODO: This is moved to ZIO 2
  val logAnnotation: FiberRef.Runtime[Map[String, String]] =
    FiberRef.unsafeMake(Map.empty, identity, (old, newV) => old ++ newV)

  /**
   * The [[logContext]] fiber reference is used to store typed, structured log
   * annotations, which can be utilized by backends to enrich log messages.
   */
  val logContext: FiberRef.Runtime[LogContext] =
    FiberRef.unsafeMake(LogContext.empty, identity, (old, newV) => old ++ newV)

  /**
   * Add annotations to log context.
   *
   * example of usage:
   * {{{
   *  ZIO.log("my message") @@ annotate("requestId" -> UUID.random.toString)
   * }}}
   */
  def annotate(annotations: (String, String)*): ZIOAspect[Nothing, Any, Nothing, Any, Nothing, Any] =
    new ZIOAspect[Nothing, Any, Nothing, Any, Nothing, Any] {
      def apply[R, E, A](zio: ZIO[R, E, A])(implicit trace: ZTraceElement): ZIO[R, E, A] =
        logAnnotation.get.flatMap(old => logAnnotation.locally(old ++ annotations.toMap)(zio))
    }

  def console(
    format: LogFormat = LogFormat.colored,
    logLevel: LogLevel = LogLevel.Info
  ): RuntimeConfigAspect = {
    val _ = (logLevel, format)
    ???
  }

  def consoleErr(
    format: LogFormat = LogFormat.default,
    logLevel: LogLevel = LogLevel.Info
  ): RuntimeConfigAspect = {
    val _ = (logLevel, format)
    ???
  }

  def file(
    destination: Path,
    format: LogFormat = LogFormat.default,
    logLevel: LogLevel = LogLevel.Info,
    charset: Charset = StandardCharsets.UTF_8,
    autoFlushBatchSize: Int = 1,
    bufferedIOSize: Option[Int] = None
  ): RuntimeConfigAspect = {
    val _         = (destination, charset, autoFlushBatchSize, bufferedIOSize, logLevel, format)
    val logWriter = new internal.LogWriter(destination, charset, autoFlushBatchSize, bufferedIOSize)

    val stringLogger: ZLogger[String, Unit] =
      format.toLogger.map { line =>
        logWriter.write(line)
      }

    val causeLogger: ZLogger[Cause[Any], Unit] =
      ??? //format.toLogger

    RuntimeConfigAspect.addLogger(stringLogger) >>> RuntimeConfigAspect.addLogger(causeLogger)
  }

  def fileAsync(
    destination: Path,
    format: LogFormat = LogFormat.default,
    logLevel: LogLevel = LogLevel.Info,
    charset: Charset = StandardCharsets.UTF_8,
    autoFlushBatchSize: Int = 1,
    bufferedIOSize: Option[Int] = None
  ): RuntimeConfigAspect = {
    val _ = (destination, charset, autoFlushBatchSize, bufferedIOSize, logLevel, format)
    ???
  }
}
