/*
 * Copyright 2019-2025 John A. De Goes and the ZIO Contributors
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

import org.slf4j.event.{ KeyValuePair, Level }
import org.slf4j.helpers.MessageFormatter
import zio.logging.LogFilter
import zio.{ Cause, Fiber, FiberId, FiberRef, FiberRefs, LogLevel, Runtime, Trace, Unsafe }

import scala.jdk.CollectionConverters._

final class ZioLoggerRuntime(runtime: Runtime[Any], filter: LogFilter[Any], config: Slf4jBridgeConfig)
    extends LoggerRuntime {

  override def log(
    logger: LoggerData,
    level: Level,
    messagePattern: String,
    arguments: Array[AnyRef],
    throwable: Throwable,
    keyValues: java.util.List[KeyValuePair]
  ): Unit =
    Unsafe.unsafe { implicit u =>
      val logLevel     = ZioLoggerRuntime.logLevelMapping(level)
      val trace        = Trace.empty
      val fiberId      = FiberId.Gen.Live.make(trace)
      val currentFiber = Fiber._currentFiber.get()

      val currentFiberRefs = if (currentFiber eq null) {
        runtime.fiberRefs.joinAs(fiberId)(FiberRefs.empty)
      } else {
        runtime.fiberRefs.joinAs(fiberId)(currentFiber.unsafe.getFiberRefs())
      }

      val logSpans = if (config.loggerNameLogSpan) {
        List(zio.LogSpan(logger.name, java.lang.System.currentTimeMillis()))
      } else {
        List.empty
      }

      val logAnnotations = if (keyValues != null && !keyValues.isEmpty) {
        keyValues.asScala.map(kv => (kv.key, kv.value.toString)).toMap ++ logger.annotations
      } else {
        logger.annotations
      }

      val fiberRefs = currentFiberRefs
        .updatedAs(fiberId)(FiberRef.currentLogSpan, logSpans ++ currentFiberRefs.getOrDefault(FiberRef.currentLogSpan))
        .updatedAs(fiberId)(
          FiberRef.currentLogAnnotations,
          currentFiberRefs.getOrDefault(FiberRef.currentLogAnnotations) ++ logAnnotations
        )

      val fiberRuntime = zio.internal.FiberRuntime(fiberId, fiberRefs, runtime.runtimeFlags)

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

      fiberRuntime.log(() => msg, cause, Some(logLevel), trace)
    }

  override def isEnabled(logger: LoggerData, level: Level): Boolean = {
    val logLevel = ZioLoggerRuntime.logLevelMapping(level)

    filter(
      Trace.empty,
      FiberId.None,
      logLevel,
      () => "",
      Cause.empty,
      FiberRefs.empty,
      List.empty,
      logger.annotations
    )
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
