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
package zio.logging.slf4j.bridge

import org.slf4j.impl.ZioLoggerFactory
import zio.{ Runtime, Semaphore, Unsafe, ZIO, ZLayer }

object Slf4jBridge {

  /**
   * log annotation key for slf4j logger name
   */
  @deprecated("use zio.logging.loggerNameAnnotationKey", "2.1.8")
  val loggerNameAnnotationKey: String = "slf4j_logger_name"

  /**
   * initialize SLF4J bridge
   */
  def initialize: ZLayer[Any, Nothing, Unit] =
    Runtime.enableCurrentFiber ++ layer(zio.logging.loggerNameAnnotationKey)

  /**
   * initialize SLF4J bridge without `FiberRef` propagation
   */
  def initializeWithoutFiberRefPropagation: ZLayer[Any, Nothing, Unit] = layer(zio.logging.loggerNameAnnotationKey)

  /**
   * initialize SLF4J bridge, where custom annotation key for logger name may be provided
   * this is to achieve backward compatibility where [[Slf4jBridge.loggerNameAnnotationKey]] was used
   *
   * NOTE: this feature may be removed in future releases
   */
  def initialize(nameAnnotationKey: String): ZLayer[Any, Nothing, Unit] =
    Runtime.enableCurrentFiber ++ layer(nameAnnotationKey)

  private val initLock = Semaphore.unsafe.make(1)(Unsafe.unsafe)

  private def layer(nameAnnotationKey: String): ZLayer[Any, Nothing, Unit] =
    ZLayer {
      for {
        runtime <- ZIO.runtime[Any]
        _       <- initLock.withPermit {
                     ZIO.succeed(ZioLoggerFactory.initialize(new ZioLoggerRuntime(runtime, nameAnnotationKey)))
                   }
      } yield ()
    }
}
