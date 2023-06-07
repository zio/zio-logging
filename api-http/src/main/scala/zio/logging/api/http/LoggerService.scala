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

  def getLoggerConfigurations(): ZIO[Any, Throwable, List[Domain.LoggerConfiguration]]

  def getLoggerConfiguration(name: String): ZIO[Any, Throwable, Option[Domain.LoggerConfiguration]]

  def setLoggerConfiguration(name: String, logLevel: LogLevel): ZIO[Any, Throwable, Domain.LoggerConfiguration]
}

object LoggerService {

  def getLoggerConfigurations(): ZIO[LoggerService, Throwable, List[Domain.LoggerConfiguration]] =
    ZIO.serviceWithZIO[LoggerService](_.getLoggerConfigurations())

  def getLoggerConfiguration(name: String): ZIO[LoggerService, Throwable, Option[Domain.LoggerConfiguration]] =
    ZIO.serviceWithZIO[LoggerService](_.getLoggerConfiguration(name))

  def setLoggerConfiguration(
    name: String,
    logLevel: LogLevel
  ): ZIO[LoggerService, Throwable, Domain.LoggerConfiguration] =
    ZIO.serviceWithZIO[LoggerService](_.setLoggerConfiguration(name, logLevel))
}
