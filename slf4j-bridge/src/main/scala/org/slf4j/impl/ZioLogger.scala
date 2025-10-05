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
package org.slf4j.impl

import org.slf4j.Marker
import org.slf4j.event.Level
import org.slf4j.helpers.ZioLoggerBase
import zio.logging.slf4j.bridge.LoggerData

final class ZioLogger(name: String, factory: ZioLoggerFactory) extends ZioLoggerBase(name) {
  private val data: LoggerData = LoggerData(name)

  override protected def log(
    level: Level,
    marker: Marker,
    messagePattern: String,
    arguments: Array[AnyRef],
    throwable: Throwable
  ): Unit =
    factory.log(data, level, marker, messagePattern, arguments, throwable)

  override protected def isEnabled(level: Level): Boolean = factory.isEnabled(data, level)
}
