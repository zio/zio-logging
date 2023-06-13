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
package zio.logging.api.http

import zio.{ LogLevel, ZIO }

trait LoggerService {

  def getLoggerConfigs(): ZIO[Any, Throwable, List[LoggerService.LoggerConfig]]

  def getLoggerConfig(name: String): ZIO[Any, Throwable, Option[LoggerService.LoggerConfig]]

  def setLoggerConfig(name: String, logLevel: LogLevel): ZIO[Any, Throwable, LoggerService.LoggerConfig]
}

object LoggerService {

  final case class LoggerConfig(name: String, logLevel: LogLevel)

  def getLoggerConfigs(): ZIO[LoggerService, Throwable, List[LoggerService.LoggerConfig]] =
    ZIO.serviceWithZIO[LoggerService](_.getLoggerConfigs())

  def getLoggerConfig(name: String): ZIO[LoggerService, Throwable, Option[LoggerService.LoggerConfig]] =
    ZIO.serviceWithZIO[LoggerService](_.getLoggerConfig(name))

  def setLoggerConfig(
    name: String,
    logLevel: LogLevel
  ): ZIO[LoggerService, Throwable, LoggerService.LoggerConfig] =
    ZIO.serviceWithZIO[LoggerService](_.setLoggerConfig(name, logLevel))
}
