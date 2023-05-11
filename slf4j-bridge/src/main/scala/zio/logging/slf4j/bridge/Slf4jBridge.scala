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
import zio.{ FiberRefs, Trace, ZIO, ZIOAspect, ZLayer }

object Slf4jBridge {

  val fiberRefsThreadLocal: ThreadLocal[FiberRefs] = new ThreadLocal[FiberRefs] {
    override def initialValue(): FiberRefs = FiberRefs.empty
  }

  def withFiberContext[R, E, A](zio: ZIO[R, E, A])(implicit trace: Trace): ZIO[R, E, A] =
    ZIO.scoped[R] {
      ZIO.acquireRelease(ZIO.getFiberRefs.map(fiberRefs => fiberRefsThreadLocal.set(fiberRefs)) *> zio)(res =>
        ZIO.succeed(fiberRefsThreadLocal.set(FiberRefs.empty)).as(res)
      )
    }

  val fiberContext: ZIOAspect[Nothing, Any, Nothing, Any, Nothing, Any] =
    new ZIOAspect[Nothing, Any, Nothing, Any, Nothing, Any] {
      def apply[R, E, A](zio: ZIO[R, E, A])(implicit trace: Trace): ZIO[R, E, A] =
        withFiberContext(zio)
    }

  /**
   * log annotation key for slf4j logger name
   */
  @deprecated("use zio.logging.loggerNameAnnotationKey", "2.1.8")
  val loggerNameAnnotationKey: String = "slf4j_logger_name"

  /**
   * initialize SLF4J bridge
   */
  def initialize: ZLayer[Any, Nothing, Unit] =
    initialize(zio.logging.loggerNameAnnotationKey)

  /**
   * initialize SLF4J bridge, where custom annotation key for logger name may be provided
   * this is to achieve backward compatibility where [[Slf4jBridge.loggerNameAnnotationKey]] was used
   *
   * NOTE: this feature may be removed in future releases
   */
  def initialize(nameAnnotationKey: String): ZLayer[Any, Nothing, Unit] =
    ZLayer {
      ZIO.runtime[Any].flatMap { runtime =>
        ZIO.succeed {
          ZioLoggerFactory.initialize(runtime, nameAnnotationKey)
        }
      }
    }
}
