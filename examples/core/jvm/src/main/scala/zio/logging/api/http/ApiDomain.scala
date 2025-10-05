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
package zio.logging.api.http

import zio.LogLevel
import zio.logging.LoggerConfigurer
import zio.schema.{ DeriveSchema, Schema }

object ApiDomain {

  sealed trait Error {
    def message: String
  }

  object Error {
    final case class NotFound(message: String = "Not Found") extends Error

    final case class Internal(message: String = "Internal") extends Error

    implicit val notFoundSchema: Schema[Error.NotFound] = DeriveSchema.gen[Error.NotFound]
    implicit val internalSchema: Schema[Error.Internal] = DeriveSchema.gen[Error.Internal]
    implicit val schema: Schema[Error]                  = DeriveSchema.gen[Error]
  }

  implicit val logLevelSchema: Schema[LogLevel] = {
    val levelToLabel: Map[LogLevel, String] = LogLevel.levels.map(level => (level, level.label)).toMap
    val labelToLevel: Map[String, LogLevel] = levelToLabel.map(_.swap)

    Schema[String]
      .transformOrFail[LogLevel](v => labelToLevel.get(v).toRight("Failed"), v => levelToLabel.get(v).toRight("Failed"))
  }

  final case class LoggerConfig(name: String, level: LogLevel)

  object LoggerConfig {
    implicit val schema: Schema[LoggerConfig] = DeriveSchema.gen[LoggerConfig]

    def from(value: LoggerConfigurer.LoggerConfig): LoggerConfig =
      LoggerConfig(value.name, value.level)
  }

}
