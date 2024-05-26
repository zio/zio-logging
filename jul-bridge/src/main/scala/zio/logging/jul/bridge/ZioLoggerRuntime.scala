/*
 * Copyright 2019-2024 John A. De Goes and the ZIO Contributors
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
package zio.logging.jul.bridge

import zio.logging.LogFilter
import zio.{ Cause, Fiber, FiberId, FiberRef, FiberRefs, LogLevel, Runtime, Trace, Unsafe }

import java.util.logging.{ Handler, Level, LogRecord }

final class ZioLoggerRuntime(runtime: Runtime[Any], filter: LogFilter[Any]) extends Handler {

  override def publish(record: LogRecord): Unit = {
    if (!isEnabled(record.getLoggerName, record.getLevel)) {
      return
    }

    Unsafe.unsafe { implicit u =>
      val msg       = record.getMessage
      val level     = record.getLevel
      val name      = record.getLoggerName
      val throwable = record.getThrown

      val logLevel     = ZioLoggerRuntime.logLevelMapping(level)
      val trace        = Trace.empty
      val fiberId      = FiberId.make(trace)
      val currentFiber = Fiber._currentFiber.get()

      val currentFiberRefs = if (currentFiber eq null) {
        runtime.fiberRefs.joinAs(fiberId)(FiberRefs.empty)
      } else {
        runtime.fiberRefs.joinAs(fiberId)(currentFiber.unsafe.getFiberRefs())
      }

      val logSpan    = zio.LogSpan(name, java.lang.System.currentTimeMillis())
      val loggerName = (zio.logging.loggerNameAnnotationKey -> name)

      val fiberRefs = currentFiberRefs
        .updatedAs(fiberId)(FiberRef.currentLogSpan, logSpan :: currentFiberRefs.getOrDefault(FiberRef.currentLogSpan))
        .updatedAs(fiberId)(
          FiberRef.currentLogAnnotations,
          currentFiberRefs.getOrDefault(FiberRef.currentLogAnnotations) + loggerName
        )

      val fiberRuntime = zio.internal.FiberRuntime(fiberId, fiberRefs, runtime.runtimeFlags)

      val cause = if (throwable != null) {
        Cause.die(throwable)
      } else {
        Cause.empty
      }

      fiberRuntime.log(() => msg, cause, Some(logLevel), trace)

    }
  }

  override def flush(): Unit = ()

  override def close(): Unit = ()

  private def isEnabled(name: String, level: Level): Boolean = {
    val logLevel = ZioLoggerRuntime.logLevelMapping(level)

    filter(
      Trace(name, "", 0),
      FiberId.None,
      logLevel,
      () => "",
      Cause.empty,
      FiberRefs.empty,
      List.empty,
      Map(zio.logging.loggerNameAnnotationKey -> name)
    )
  }
}

object ZioLoggerRuntime {

  private val logLevelMapping: Map[Level, LogLevel] = Map(
    Level.SEVERE  -> LogLevel.Fatal,
    Level.WARNING -> LogLevel.Warning,
    Level.INFO    -> LogLevel.Info,
    Level.CONFIG  -> LogLevel.Info,
    Level.FINE    -> LogLevel.Debug,
    Level.FINER   -> LogLevel.Debug,
    Level.FINEST  -> LogLevel.Debug,
    Level.ALL     -> LogLevel.All,
    Level.OFF     -> LogLevel.None
  )
}
