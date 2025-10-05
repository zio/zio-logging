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

import org.slf4j.event.{ KeyValuePair, Level }
import org.slf4j.{ ILoggerFactory, Logger }

import java.util.concurrent.ConcurrentHashMap
import scala.jdk.CollectionConverters._

final class ZioLoggerFactory extends ILoggerFactory {
  private var runtime: LoggerRuntime = null
  private val loggers                = new ConcurrentHashMap[String, Logger]().asScala

  private[bridge] def attachRuntime(runtime: LoggerRuntime): Unit =
    this.runtime = runtime

  private[bridge] def log(
    logger: LoggerData,
    level: Level,
    messagePattern: String,
    arguments: Array[AnyRef],
    throwable: Throwable,
    keyValues: java.util.List[KeyValuePair]
  ): Unit =
    if (runtime != null) runtime.log(logger, level, messagePattern, arguments, throwable, keyValues)

  private[bridge] def isEnabled(logger: LoggerData, level: Level): Boolean =
    if (runtime != null) runtime.isEnabled(logger, level) else false

  override def getLogger(name: String): Logger =
    loggers.getOrElseUpdate(name, new ZioLogger(name, this))
}

object ZioLoggerFactory {

  def initialize(runtime: LoggerRuntime): Unit = {
    val factory = org.slf4j.LoggerFactory
      .getILoggerFactory()
      .asInstanceOf[ZioLoggerFactory]

    factory.attachRuntime(runtime)
  }

}
