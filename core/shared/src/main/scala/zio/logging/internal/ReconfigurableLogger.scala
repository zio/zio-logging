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
package zio.logging.internal

import zio.prelude._
import zio.{ Cause, FiberId, FiberRefs, LogLevel, LogSpan, Trace, ZLogger }

import java.util.concurrent.atomic.AtomicReference

private[logging] sealed trait ReconfigurableLogger[-Message, +Output, Config] extends ZLogger[Message, Output] {

  def reconfigure(config: Config): Unit

  def reconfigureIfChanged(config: Config): Boolean
}

private[logging] object ReconfigurableLogger {

  def apply[M, O, C: Equal](
    config: C,
    makeLogger: C => ZLogger[M, O]
  ): ReconfigurableLogger[M, O, C] =
    new ReconfigurableLogger[M, O, C] {

      private val configureLogger: AtomicReference[(C, ZLogger[M, O])] = {
        val logger = makeLogger(config)
        new AtomicReference[(C, ZLogger[M, O])]((config, logger))
      }

      override def reconfigureIfChanged(config: C): Boolean = {
        val currentConfig = configureLogger.get()._1
        if (currentConfig !== config) {
          reconfigure(config)
          true
        } else false
      }

      override def reconfigure(config: C): Unit = {
        val logger = makeLogger(config)
        configureLogger.set((config, logger))
      }

      override def apply(
        trace: Trace,
        fiberId: FiberId,
        logLevel: LogLevel,
        message: () => M,
        cause: Cause[Any],
        context: FiberRefs,
        spans: List[LogSpan],
        annotations: Map[String, String]
      ): O =
        configureLogger.get()._2.apply(trace, fiberId, logLevel, message, cause, context, spans, annotations)
    }

}
