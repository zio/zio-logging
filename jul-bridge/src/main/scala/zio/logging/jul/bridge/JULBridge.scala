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
package zio.logging.jul.bridge

import zio.logging.LogFilter
import zio.{ Config, NonEmptyChunk, Runtime, Semaphore, Unsafe, ZIO, ZLayer }

import java.io.ByteArrayInputStream
import java.util.logging.LogManager

/**
 * A bridge that forwards `java.util.logging` (JUL) records to the ZIO loggers, so that Java libraries that log through
 * JUL get the same format, filter and destination as ZIO log lines. Installing the bridge resets the global JUL
 * configuration with the single setting `.level= ALL`, which removes the handlers installed before on the root JUL
 * logger and on named JUL loggers, and then adds one handler to the root JUL logger.
 *
 * The bridge captures the ZIO runtime, with the loggers it has at that moment, when it is installed, so build the
 * logger layer first. Each forwarded JUL record becomes one ZIO log line with the JUL logger name in the `logger_name`
 * annotation; the JUL message is used as it is, without filling in its parameters.
 *
 * @see [[https://zio.dev/zio-logging/jul-bridge/ java.util.logging bridge]]
 */
object JULBridge {

  /**
   * The default config path of the bridge's filter, `logger.filter`.
   */
  val logFilterConfigPath: NonEmptyChunk[String] = zio.logging.loggerConfigPath :+ "filter"

  /**
   * A layer that installs the bridge with no filter, so that JUL records at every standard JUL level are forwarded,
   * and turns on fiber-ref propagation. Fiber-ref propagation, which `Runtime.enableCurrentFiber` turns on, lets a JUL
   * record logged on the thread of a fiber carry that fiber's log annotations and log spans.
   */
  def initialize: ZLayer[Any, Nothing, Unit] = init(LogFilter.acceptAll)

  /**
   * A layer that installs the bridge with `filter` and turns on fiber-ref propagation. The filter sees the level and
   * the `logger_name` annotation of each record, which holds the JUL logger name.
   *
   * {{{
   * import zio._
   * import zio.logging.{ LogFilter, consoleLogger }
   * import zio.logging.jul.bridge.JULBridge
   *
   * val logger: ZLayer[Any, Config.Error, Unit] =
   *   Runtime.removeDefaultLoggers >>> consoleLogger() >+> JULBridge.init(
   *     LogFilter.logLevelByName(LogLevel.Info, "io.opentelemetry" -> LogLevel.Warning)
   *   )
   * }}}
   *
   * @param filter the filter that decides which JUL records are forwarded
   */
  def init(filter: LogFilter[Any]): ZLayer[Any, Nothing, Unit] = Runtime.enableCurrentFiber ++ layer(filter)

  /**
   * A layer that installs the bridge with a filter read from config, and turns on fiber-ref propagation. It reads a
   * `LogFilter.LogLevelByNameConfig` at `configPath` from the `ConfigProvider` of the runtime: the `rootLevel` key
   * (`INFO` when missing) and the `mappings` table from JUL logger name prefix to level. It fails with a
   * `Config.Error` when the config cannot be read.
   *
   * {{{
   * import zio._
   * import zio.logging.consoleLogger
   * import zio.logging.jul.bridge.JULBridge
   *
   * object LegacyApp extends ZIOAppDefault {
   *   override val bootstrap: ZLayer[ZIOAppArgs, Any, Any] =
   *     Runtime.removeDefaultLoggers >>> consoleLogger() >+> JULBridge.init()
   *
   *   override def run: ZIO[Any, Any, Unit] =
   *     ZIO.succeed(java.util.logging.Logger.getLogger("com.example.Legacy").info("Legacy info"))
   * }
   * }}}
   *
   * With the default path and a HOCON `ConfigProvider`, such as `TypesafeConfigProvider` from `zio-config-typesafe`,
   * the following config keeps `INFO` and above, and only `WARNING` and above for the JUL loggers under
   * `io.opentelemetry`:
   *
   * {{{
   * // no-check: HOCON config input
   * logger {
   *   filter {
   *     rootLevel = INFO
   *     mappings {
   *       "io.opentelemetry" = WARN
   *     }
   *   }
   * }
   * }}}
   *
   * @param configPath the config path of the filter, [[logFilterConfigPath]] by default
   * @see [[https://zio.dev/zio-logging/jul-bridge/ java.util.logging bridge]]
   */
  def init(configPath: NonEmptyChunk[String] = logFilterConfigPath): ZLayer[Any, Config.Error, Unit] =
    Runtime.enableCurrentFiber ++ layer(configPath)

  /**
   * A layer that installs the bridge with no filter, so that JUL records at every standard JUL level are forwarded,
   * without turning on fiber-ref propagation: forwarded lines do not carry the log annotations and log spans of the
   * fiber that logged them.
   */
  def initializeWithoutFiberRefPropagation: ZLayer[Any, Nothing, Unit] = initWithoutFiberRefPropagation(
    LogFilter.acceptAll
  )

  /**
   * A layer that installs the bridge with `filter`, without turning on fiber-ref propagation: forwarded lines do not
   * carry the log annotations and log spans of the fiber that logged them.
   *
   * @param filter the filter that decides which JUL records are forwarded
   */
  def initWithoutFiberRefPropagation(filter: LogFilter[Any]): ZLayer[Any, Nothing, Unit] = layer(filter)

  private val initLock = Semaphore.unsafe.make(1)(Unsafe.unsafe)

  private def layer(filter: LogFilter[Any]): ZLayer[Any, Nothing, Unit] =
    ZLayer(make(filter))

  private def layer(configPath: NonEmptyChunk[String]): ZLayer[Any, Config.Error, Unit] =
    ZLayer(make(configPath))

  /**
   * Installs the bridge with `filter`, using the runtime of the fiber that runs this effect, without turning on
   * fiber-ref propagation. Each run resets the global JUL configuration, which removes the handler of an earlier run,
   * and adds a new handler.
   *
   * @param filter the filter that decides which JUL records are forwarded
   */
  def make(filter: LogFilter[Any]): ZIO[Any, Nothing, Unit] =
    for {
      runtime <- ZIO.runtime[Any]
      _       <- initLock.withPermit {
                   ZIO.succeed {
                     val configuration = ".level= ALL"
                     LogManager.getLogManager.readConfiguration(new ByteArrayInputStream(configuration.getBytes))

                     java.util.logging.Logger
                       .getLogger("")
                       .addHandler(new ZioLoggerRuntime(runtime, filter))
                   }
                 }
    } yield ()

  /**
   * Installs the bridge with a `LogFilter.LogLevelByNameConfig` filter read from config at `configPath`, using the
   * runtime of the fiber that runs this effect, without turning on fiber-ref propagation. It fails with a
   * `Config.Error` when the config cannot be read.
   *
   * @param configPath the config path of the filter, [[logFilterConfigPath]] by default
   */
  def make(configPath: NonEmptyChunk[String] = logFilterConfigPath): ZIO[Any, Config.Error, Unit] =
    LogFilter.LogLevelByNameConfig.load(configPath).flatMap(c => make(c.toFilter))

}
