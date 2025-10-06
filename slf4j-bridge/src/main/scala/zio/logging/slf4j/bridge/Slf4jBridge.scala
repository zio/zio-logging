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

import org.slf4j.impl.ZioLoggerFactory
import zio.logging.LogFilter
import zio.{ Config, NonEmptyChunk, RuntimeFlag, RuntimeFlags, Scope, Semaphore, Unsafe, ZIO, ZLayer }

object Slf4jBridge {

  val bridgeConfigPath: NonEmptyChunk[String] = bridgeConfigPath(zio.logging.loggerConfigPath)

  def bridgeConfigPath(path: NonEmptyChunk[String]): NonEmptyChunk[String] = path :+ "bridge"

  def logFilterConfigPath(path: NonEmptyChunk[String] = zio.logging.loggerConfigPath): NonEmptyChunk[String] =
    path :+ "filter"

  /**
   * initialize SLF4J bridge
   */
  def initialize: ZLayer[Any, Nothing, Unit] = init(LogFilter.acceptAll)

  /**
   * initialize SLF4J bridge with `LogFilter`
   * @param filter Log filter
   */
  def init(filter: LogFilter[Any]): ZLayer[Any, Nothing, Unit] = layer(filter, Slf4jBridgeConfig.default)

  /**
   * initialize SLF4J bridge with `LogFilter` from configuration
   * @param configPath configuration path
   */
  def init(configPath: NonEmptyChunk[String] = zio.logging.loggerConfigPath): ZLayer[Any, Config.Error, Unit] =
    layer(configPath)

  /**
   * initialize SLF4J bridge without `FiberRef` propagation
   */
  def initializeWithoutFiberRefPropagation: ZLayer[Any, Nothing, Unit] = layer(
    LogFilter.acceptAll,
    Slf4jBridgeConfig(fiberRefPropagation = false, loggerNameLogSpan = true)
  )

  private val initLock = Semaphore.unsafe.make(1)(Unsafe.unsafe)

  private def layer(filter: LogFilter[Any], config: Slf4jBridgeConfig): ZLayer[Any, Nothing, Unit] =
    ZLayer.scoped[Any](make(filter, config))

  private def layer(configPath: NonEmptyChunk[String]): ZLayer[Any, Config.Error, Unit] =
    ZLayer.scoped[Any](make(configPath))

  def make(configPath: NonEmptyChunk[String] = zio.logging.loggerConfigPath): ZIO[Scope, Config.Error, Unit] =
    for {
      filterConfig <- LogFilter.LogLevelByNameConfig.load(logFilterConfigPath(configPath))
      bridgeConfig <- Slf4jBridgeConfig.load(bridgeConfigPath(configPath))
      _            <- make(filterConfig.toFilter, bridgeConfig)
    } yield ()

  def make(filter: LogFilter[Any], config: Slf4jBridgeConfig): ZIO[Scope, Nothing, Unit] =
    for {
      _       <- ZIO.when(config.fiberRefPropagation) {
                   ZIO.withRuntimeFlagsScoped(RuntimeFlags.enable(RuntimeFlag.CurrentFiber))
                 }
      runtime <- ZIO.runtime[Any]
      _       <- initLock.withPermit {
                   ZIO.succeed(ZioLoggerFactory.initialize(new ZioLoggerRuntime(runtime, filter, config)))
                 }
    } yield ()
}
