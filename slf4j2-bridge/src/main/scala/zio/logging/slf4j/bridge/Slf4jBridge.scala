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

import zio.logging.LogFilter
import zio.{ Runtime, Semaphore, Unsafe, ZIO, ZLayer }

object Slf4jBridge {

  /**
   * initialize SLF4J bridge
   */
  def initialize: ZLayer[Any, Nothing, Unit] = initialize(LogFilter.acceptAll)

  def initialize(filter: LogFilter[Any]): ZLayer[Any, Nothing, Unit] = Runtime.enableCurrentFiber ++ layer(filter)

  /**
   * initialize SLF4J bridge without `FiberRef` propagation
   */
  def initializeWithoutFiberRefPropagation: ZLayer[Any, Nothing, Unit] = initializeWithoutFiberRefPropagation(
    LogFilter.acceptAll
  )

  def initializeWithoutFiberRefPropagation(filter: LogFilter[Any]): ZLayer[Any, Nothing, Unit] = layer(filter)

  private val initLock = Semaphore.unsafe.make(1)(Unsafe.unsafe)

  private def layer(filter: LogFilter[Any]): ZLayer[Any, Nothing, Unit] =
    ZLayer(make(filter))

  def make(filter: LogFilter[Any]): ZIO[Any, Nothing, Unit] =
    for {
      runtime <- ZIO.runtime[Any]
      _       <- initLock.withPermit {
                   ZIO.succeed(
                     org.slf4j.LoggerFactory
                       .getILoggerFactory()
                       .asInstanceOf[LoggerFactory]
                       .attachRuntime(new ZioLoggerRuntime(runtime, filter))
                   )
                 }
    } yield ()
}
