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
package zio.logging

import zio.{ Config, LogLevel }

final case class ConsoleJsonLoggerConfig(format: LogFormat, filter: LogFilter[String])

object ConsoleJsonLoggerConfig {

  def apply(labelPatterns: Map[String, LogPattern], filter: LogFilter[String]): ConsoleJsonLoggerConfig =
    ConsoleJsonLoggerConfig(LogFormat.makeLabeled(labelPatterns), filter)

  val config: Config[ConsoleJsonLoggerConfig] = {
    val patternConfig = Config.table("pattern", LogPattern.config).withDefault(Map.empty)
    val filterConfig  = LogFilter.LogLevelByNameConfig.config.nested("filter")
    (patternConfig ++ filterConfig).map { case (pattern, filterConfig) =>
      ConsoleJsonLoggerConfig(pattern, LogFilter.logLevelByName(filterConfig))
    }
  }

  val default: ConsoleJsonLoggerConfig = ConsoleJsonLoggerConfig(LogFormat.default, LogFilter.logLevel(LogLevel.Info))
}
