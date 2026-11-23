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
package zio.logging.slf4j.bridge

import org.slf4j.event.{ KeyValuePair, Level }

/**
 * The interface through which the bridge's loggers hand SLF4J calls to the ZIO runtime.
 */
trait LoggerRuntime {

  /**
   * Logs one SLF4J call of `logger` at `level`.
   */
  def log(
    logger: LoggerData,
    level: Level,
    messagePattern: String,
    arguments: Array[AnyRef],
    throwable: Throwable,
    keyValues: java.util.List[KeyValuePair]
  ): Unit

  /**
   * Returns whether `logger` logs at `level`.
   */
  def isEnabled(logger: LoggerData, level: Level): Boolean
}
