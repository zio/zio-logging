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
package org.slf4j.impl

import com.github.ghik.silencer.silent
import org.slf4j.{ ILoggerFactory, Logger }
import zio.ZIO
import zio.logging.slf4j.bridge.Slf4jBridge

import java.util.concurrent.ConcurrentHashMap
import scala.jdk.CollectionConverters._

class ZioLoggerFactory extends ILoggerFactory {
  private var runtime: zio.Runtime[Any] = null
  private var annotationKey: String     = Slf4jBridge.loggerNameAnnotationKey
  private val loggers                   = new ConcurrentHashMap[String, Logger]().asScala: @silent("JavaConverters")

  def attachRuntime(runtime: zio.Runtime[Any]): Unit =
    this.runtime = runtime

  def setNameAnnotationKey(annotationKey: String): Unit = {
    if (annotationKey == null) {
      throw new IllegalArgumentException("Name annotation key is required")
    }
    this.annotationKey = annotationKey
  }

  private[impl] def nameAnnotationKey = annotationKey

  private[impl] def run(f: ZIO[Any, Nothing, Any]): Unit =
    if (runtime != null) {
      zio.Unsafe.unsafe { implicit u =>
        runtime.unsafe.run {
          val fiberRefs = Slf4jBridge.fiberRefsThreadLocal.get()

          ZIO.setFiberRefs(fiberRefs) *> f
        }
        ()
      }
    }

  override def getLogger(name: String): Logger =
    loggers.getOrElseUpdate(name, new ZioLogger(name, this))
}

object ZioLoggerFactory {

  /**
   * initialize logger factory
   */
  def initialize(runtime: zio.Runtime[Any]): Unit =
    initialize(runtime, zio.logging.loggerNameAnnotationKey)

  /**
   * initialize logger factory, where custom annotation key for logger name may be provided
   * this is to achieve backward compatibility where [[Slf4jBridge.loggerNameAnnotationKey]] was used
   *
   * NOTE: this feature may be removed in future releases
   */
  def initialize(runtime: zio.Runtime[Any], nameAnnotationKey: String): Unit = {
    val factory = StaticLoggerBinder.getSingleton.getLoggerFactory
      .asInstanceOf[ZioLoggerFactory]

    factory.attachRuntime(runtime)
    factory.setNameAnnotationKey(nameAnnotationKey)
  }

}
