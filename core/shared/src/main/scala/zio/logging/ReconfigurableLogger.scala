/*
 * Copyright 2019-2026 John A. De Goes and the ZIO Contributors
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

/**
 * A logger whose config and underlying logger can be replaced while the program runs. Each log event is
 * passed to the logger that is current at that moment. `ReconfigurableLogger.make` reloads the config on a
 * schedule and builds a new logger when the config has changed.
 *
 * @see [[https://zio.dev/zio-logging/reconfigurable-logger/ Reconfigurable Logger]]
 */
sealed trait ReconfigurableLogger[-Message, +Output, Config] extends ZLogger[Message, Output] {

  /** Returns the current config and the logger built from it. */
  def get: (Config, ZLogger[Message, Output])

  /** Replaces the current config and logger; log events after this call go to `logger`. */
  def set[M <: Message, O >: Output](config: Config, logger: ZLogger[M, O]): Unit
}

/**
 * Constructors for `ReconfigurableLogger`.
 */
object ReconfigurableLogger {

  /** Creates a reconfigurable logger that starts with `config` and `logger`; change them with `set`. */
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

  /**
   * Loads the config with `loadConfig` and builds the first logger with `makeLogger(config, None)`. It then
   * forks a fiber into the `Scope` that runs `loadConfig` again on the `updateLogger` schedule (every 10
   * seconds by default; with `Schedule.fixed`, the first reload comes one interval after the start). When a
   * newly loaded config is not equal to the current one, according to the `Equal` instance, it calls
   * `makeLogger(newConfig, Some(currentLogger))` and switches to the new logger. A failure of `loadConfig` or
   * `makeLogger` in a reload ends the reloads; the current logger stays in use. A `ConsoleLoggerConfig` or
   * `FileLoggerConfig` whose format has a `%timestamp{pattern}` is never equal to the previous one, so the
   * logger is rebuilt on every reload.
   *
   * {{{
   * import zio._
   * import zio.logging.{ ConsoleLoggerConfig, ReconfigurableLogger, ZLoggerZIOLayerOps, makeConsoleLogger }
   *
   * val logger: ZLayer[Any, Config.Error, Unit] =
   *   ReconfigurableLogger
   *     .make[Any, Config.Error, String, Any, ConsoleLoggerConfig](
   *       ConsoleLoggerConfig.load(),
   *       (config, _) => makeConsoleLogger(config),
   *       Schedule.fixed(30.seconds)
   *     )
   *     .installUnscoped[Any]
   * }}}
   *
   * @see [[https://zio.dev/zio-logging/reconfigurable-logger/ Reconfigurable Logger]]
   */
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
