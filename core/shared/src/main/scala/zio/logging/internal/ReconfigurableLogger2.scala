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

import zio._
import zio.prelude._

import java.util.concurrent.atomic.AtomicReference

private[logging] sealed trait ReconfigurableLogger2[-Message, +Output, Config] extends ZLogger[Message, Output] {

  def config: Config

  def underlying: ZLogger[Message, Output]

  private[logging] def set[M <: Message, O >: Output](config: Config, logger: ZLogger[M, O]): Unit
}

private[logging] object ReconfigurableLogger2 {

  def apply[Message, Output, Config](
    config: Config,
    logger: ZLogger[Message, Output]
  ): ReconfigurableLogger2[Message, Output, Config] = {
    val configuredLogger: AtomicReference[(Config, ZLogger[Message, Output])] =
      new AtomicReference[(Config, ZLogger[Message, Output])]((config, logger))

    new ReconfigurableLogger2[Message, Output, Config] {

      override def config: Config = configuredLogger.get()._1

      override def underlying: ZLogger[Message, Output] = configuredLogger.get()._2

      override private[logging] def set[M <: Message, O >: Output](config: Config, logger: ZLogger[M, O]): Unit =
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

  def make[E, M, O, C: Equal](
    loadConfig: => ZIO[Any, E, C],
    makeLogger: (C, Option[ZLogger[M, O]]) => ZIO[Any, E, ZLogger[M, O]],
    updateLogger: Schedule[Any, Any, Any] = Schedule.fixed(10.seconds)
  ): ZIO[Scope, E, ReconfigurableLogger2[M, O, C]] =
    for {
      initialConfig       <- loadConfig
      initialLogger       <- makeLogger(initialConfig, None)
      reconfigurableLogger = ReconfigurableLogger2[M, O, C](initialConfig, initialLogger)
      _                   <- loadConfig.flatMap { newConfig =>
                               val currentConfig = reconfigurableLogger.config
                               if (currentConfig !== newConfig) {
                                 makeLogger(newConfig, Some(reconfigurableLogger.underlying)).map { newLogger =>
                                   reconfigurableLogger.set(newConfig, newLogger)
                                 }.unit
                               } else ZIO.unit
                             }.scheduleFork(updateLogger)
    } yield reconfigurableLogger

}
