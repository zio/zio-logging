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
package zio.logging.slf4j.bridge

import org.slf4j.impl.ZioLoggerFactory
import zio.logging.LogFilter
import zio.{ Config, NonEmptyChunk, Runtime, Semaphore, Unsafe, ZIO, ZLayer }

object Slf4jBridge {

  val logFilterConfigPath: NonEmptyChunk[String] = zio.logging.loggerConfigPath :+ "filter"

  /**
   * initialize SLF4J bridge
   */
  def initialize: ZLayer[Any, Nothing, Unit] = init(LogFilter.acceptAll)

  /**
   * initialize SLF4J bridge with `LogFilter`
   * @param filter Log filter
   */
  def init(filter: LogFilter[Any]): ZLayer[Any, Nothing, Unit] = Runtime.enableCurrentFiber ++ layer(filter)

  /**
   * initialize SLF4J bridge with `LogFilter` from configuration
   * @param configPath configuration path
   */
  def init(configPath: NonEmptyChunk[String] = logFilterConfigPath): ZLayer[Any, Config.Error, Unit] =
    Runtime.enableCurrentFiber ++ layer(configPath)

  /**
   * initialize SLF4J bridge without `FiberRef` propagation
   */
  def initializeWithoutFiberRefPropagation: ZLayer[Any, Nothing, Unit] = initWithoutFiberRefPropagation(
    LogFilter.acceptAll
  )

  /**
   * initialize SLF4J bridge with `LogFilter`, without `FiberRef` propagation
   * @param filter Log filter
   */
  def initWithoutFiberRefPropagation(filter: LogFilter[Any]): ZLayer[Any, Nothing, Unit] = layer(filter)

  private val initLock = Semaphore.unsafe.make(1)(Unsafe.unsafe)

  private def layer(filter: LogFilter[Any]): ZLayer[Any, Nothing, Unit] =
    ZLayer(make(filter))

  private def layer(configPath: NonEmptyChunk[String]): ZLayer[Any, Config.Error, Unit] =
    ZLayer(make(configPath))

  def make(filter: LogFilter[Any]): ZIO[Any, Nothing, Unit] =
    for {
      runtime <- ZIO.runtime[Any]
      _       <- initLock.withPermit {
                   ZIO.succeed(ZioLoggerFactory.initialize(new ZioLoggerRuntime(runtime, filter)))
                 }
    } yield ()

  def make(configPath: NonEmptyChunk[String] = logFilterConfigPath): ZIO[Any, Config.Error, Unit] =
    LogFilter.LogLevelByNameConfig.load(configPath).flatMap(c => make(c.toFilter))

}
