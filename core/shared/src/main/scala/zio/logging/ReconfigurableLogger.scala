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
package zio.logging

import zio._
import zio.prelude._

import java.util.concurrent.atomic.AtomicReference

sealed trait ReconfigurableLogger[-Message, +Output, Config] extends ZLogger[Message, Output] {

  def get: (Config, ZLogger[Message, Output])

  def set[M <: Message, O >: Output](config: Config, logger: ZLogger[M, O]): Unit
}

object ReconfigurableLogger {

  def apply[Message, Output, Config](
    config: Config,
    logger: ZLogger[Message, Output]
  ): ReconfigurableLogger[Message, Output, Config] = {
    val configuredLogger: AtomicReference[(Config, ZLogger[Message, Output])] =
      new AtomicReference[(Config, ZLogger[Message, Output])]((config, logger))

    new ReconfigurableLogger[Message, Output, Config] {

      override def get: (Config, ZLogger[Message, Output]) = configuredLogger.get()

      override def set[M <: Message, O >: Output](config: Config, logger: ZLogger[M, O]): Unit =
        configuredLogger.set((config, logger.asInstanceOf[ZLogger[Message, Output]]))

      override def apply(
        trace: Trace,
        fiberId: FiberId,
        logLevel: LogLevel,
        message: () => Message,
        cause: Cause[Any],
        context: FiberRefs,
        spans: List[LogSpan],
        annotations: Map[String, String]
      ): Output =
        configuredLogger.get()._2.apply(trace, fiberId, logLevel, message, cause, context, spans, annotations)
    }
  }

  def make[R, E, M, O, C: Equal](
    loadConfig: => ZIO[R, E, C],
    makeLogger: (C, Option[ZLogger[M, O]]) => ZIO[R, E, ZLogger[M, O]],
    updateLogger: Schedule[R, Any, Any] = Schedule.fixed(10.seconds)
  ): ZIO[R with Scope, E, ReconfigurableLogger[M, O, C]] =
    for {
      initialConfig       <- loadConfig
      initialLogger       <- makeLogger(initialConfig, None)
      reconfigurableLogger = ReconfigurableLogger[M, O, C](initialConfig, initialLogger)
      _                   <- loadConfig.flatMap { newConfig =>
                               val (currentConfig, currentLogger) = reconfigurableLogger.get
                               if (currentConfig !== newConfig) {
                                 makeLogger(newConfig, Some(currentLogger)).map { newLogger =>
                                   reconfigurableLogger.set(newConfig, newLogger)
                                 }.unit
                               } else ZIO.unit
                             }.schedule(updateLogger).forkScoped
    } yield reconfigurableLogger

}
