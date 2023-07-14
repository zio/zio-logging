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

package object logging extends LoggerLayers {

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

  val loggerConfigPath: NonEmptyChunk[String] = NonEmptyChunk("logger")

  /**
   * Logger name aspect, by this aspect is possible to set logger name (in general, logger name is extracted from [[Trace]])
   *
   * annotation key: [[zio.logging.loggerNameAnnotationKey]]
   */
  def loggerName(value: String): ZIOAspect[Nothing, Any, Nothing, Any, Nothing, Any] =
    ZIOAspect.annotated(loggerNameAnnotationKey, value)

  val removeDefaultLoggers: ZLayer[Any, Nothing, Unit] = Runtime.removeDefaultLoggers

  implicit final class LogAnnotationZIOSyntax[R, E, A](private val self: ZIO[R, E, A]) {
    def logAnnotate[V: Tag](key: LogAnnotation[V], value: V): ZIO[R, E, A] =
      self @@ key(value)
  }

  implicit final class ZLoggerOps[-Message, +Output](private val self: ZLogger[Message, Output]) {

    /**
     * Returns a version of logger that only logs messages when this filter is satisfied
     */
    def filter[M <: Message](filter: LogFilter[M]): ZLogger[M, Option[Output]] = FilteredLogger(self, filter)
  }
}
