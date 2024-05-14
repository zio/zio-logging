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
package org.slf4j.impl

import com.github.ghik.silencer.silent
import org.slf4j.event.Level
import org.slf4j.{ ILoggerFactory, Logger, Marker }

import java.util.concurrent.ConcurrentHashMap
import scala.jdk.CollectionConverters._

class ZioLoggerFactory extends ILoggerFactory {
  private var runtime: LoggerRuntime = null
  private val loggers                = new ConcurrentHashMap[String, Logger]().asScala: @silent("JavaConverters")

  private[impl] def attachRuntime(runtime: LoggerRuntime): Unit =
    this.runtime = runtime

  private[impl] def log(
    name: String,
    level: Level,
    marker: Marker,
    messagePattern: String,
    arguments: Array[AnyRef],
    throwable: Throwable
  ): Unit =
    if (runtime != null) runtime.log(name, level, marker, messagePattern, arguments, throwable)

  private[impl] def isEnabled(name: String, level: Level): Boolean =
    if (runtime != null) runtime.isEnabled(name, level) else false

  override def getLogger(name: String): Logger =
    loggers.getOrElseUpdate(name, new ZioLogger(name, this))
}

object ZioLoggerFactory {

  def initialize(runtime: LoggerRuntime): Unit = {
    val factory = StaticLoggerBinder.getSingleton.getLoggerFactory
      .asInstanceOf[ZioLoggerFactory]

    factory.attachRuntime(runtime)
  }

}
