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
package zio.logging

import zio.prelude._
import zio.{ Config, NonEmptyChunk, ZIO, ZLayer }

final case class ConsoleLoggerConfig(
  format: LogFormat,
  filter: LogFilter.LogLevelByNameConfig
) {
  def toFilter[M]: LogFilter[M] = filter.toFilter
}

object ConsoleLoggerConfig {

  val default: ConsoleLoggerConfig = ConsoleLoggerConfig(LogFormat.default, LogFilter.LogLevelByNameConfig.default)

  val config: Config[ConsoleLoggerConfig] = {
    val formatConfig = LogFormat.config.nested("format").withDefault(LogFormat.default)
    val filterConfig = LogFilter.LogLevelByNameConfig.config.nested("filter")
    (formatConfig ++ filterConfig).map { case (format, filter) =>
      ConsoleLoggerConfig(
        format,
        filter
      )
    }
  }

  implicit val equal: Equal[ConsoleLoggerConfig] = Equal.make { (l, r) =>
    l.format == r.format && l.filter === r.filter
  }

  def load(configPath: NonEmptyChunk[String] = loggerConfigPath): ZIO[Any, Config.Error, ConsoleLoggerConfig] =
    ZIO.config(ConsoleLoggerConfig.config.nested(configPath.head, configPath.tail: _*))

  def make(
    configPath: NonEmptyChunk[String] = loggerConfigPath
  ): ZLayer[Any, Config.Error, ConsoleLoggerConfig] =
    ZLayer.fromZIO(load(configPath))

}
