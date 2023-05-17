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
import zio.{ Cause, Fiber, FiberId, FiberRef, FiberRefs, LogLevel, Runtime, Trace, Unsafe, ZLogger }

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
      val logLevel = ZioLoggerRuntime.logLevelMapping(level)
      val trace    = Trace.empty
      val fiberId  = FiberId.make(trace)
      val fiber    = Fiber._currentFiber.get()

      val currentFiberRefs = if (fiber eq null) {
        runtime.fiberRefs.joinAs(fiberId)(FiberRefs.empty)
      } else {
        runtime.fiberRefs.joinAs(fiberId)(fiber.unsafe.getFiberRefs())
      }

      val logSpan    = zio.LogSpan(name, java.lang.System.currentTimeMillis())
      val loggerName = (zio.logging.loggerNameAnnotationKey -> name)

      val fiberRefs = currentFiberRefs
        .updatedAs(fiberId)(FiberRef.currentLogSpan, logSpan :: currentFiberRefs.getOrDefault(FiberRef.currentLogSpan))
        .updatedAs(fiberId)(
          FiberRef.currentLogAnnotations,
          currentFiberRefs.getOrDefault(FiberRef.currentLogAnnotations) + loggerName
        )

      lazy val msg = if (arguments != null) {
        MessageFormatter.arrayFormat(messagePattern, arguments.toArray).getMessage
      } else {
        messagePattern
      }

      val cause = if (throwable != null) {
        Cause.die(throwable)
      } else {
        Cause.empty
      }

      val spans                              = fiberRefs.getOrDefault(FiberRef.currentLogSpan)
      val annotations                        = fiberRefs.getOrDefault(FiberRef.currentLogAnnotations)
      val loggers: Set[ZLogger[String, Any]] = fiberRefs.getOrDefault(FiberRef.currentLoggers)

      loggers.foreach { logger =>
        logger(trace, fiberId, logLevel, () => msg, cause, fiberRefs, spans, annotations)
      }
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
