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
package zio.logging.slf4j.bridge

import org.slf4j.Marker
import org.slf4j.event.Level
import org.slf4j.helpers.MessageFormatter
import zio.{ Cause, LogLevel, Runtime, Unsafe, ZIO }

final class ZioLoggerRuntime(runtime: Runtime[Any]) extends LoggerRuntime {

  override def log(
    name: String,
    level: Level,
    marker: Marker,
    messagePattern: String,
    arguments: Array[AnyRef],
    throwable: Throwable
  ): Unit =
    Unsafe.unsafe { implicit u =>
      runtime.unsafe.run {
        val logLevel = ZioLoggerRuntime.logLevelMapping(level)
        ZIO.logSpan(name) {
          ZIO.logAnnotate(Slf4jBridge.loggerNameAnnotationKey, name) {
            ZIO.logLevel(logLevel) {
              lazy val msg = if (arguments != null) {
                MessageFormatter.arrayFormat(messagePattern, arguments.toArray).getMessage
              } else {
                messagePattern
              }
              val cause    = if (throwable != null) {
                Cause.die(throwable)
              } else {
                Cause.empty
              }

              ZIO.logCause(msg, cause)
            }
          }
        }
      }
      ()
    }
}

object ZioLoggerRuntime {

  val logLevelMapping: Map[Level, LogLevel] = Map(
    Level.TRACE -> LogLevel.Trace,
    Level.DEBUG -> LogLevel.Debug,
    Level.INFO  -> LogLevel.Info,
    Level.WARN  -> LogLevel.Warning,
    Level.ERROR -> LogLevel.Error
  )
}
