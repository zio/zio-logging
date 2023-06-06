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

import zio.ZIO

object ApiHandlers {

  def getLoggerConfigurations(rootPath: String) =
    ApiEndpoints
      .getLoggerConfigurations(rootPath)
      .implement(_ => LoggerService.getLoggerConfigurations().mapError(_ => "Internal Error"))

  def getLoggerConfiguration(rootPath: String) =
    ApiEndpoints
      .getLoggerConfiguration(rootPath)
      .implement { name =>
        LoggerService.getLoggerConfiguration(name).mapError(_ => "Internal Error").flatMap {
          case Some(r) => ZIO.succeed(r)
          case None    => ZIO.fail("Not Found")
        }
      }

  def setLoggerConfigurations(rootPath: String) =
    ApiEndpoints
      .setLoggerConfiguration(rootPath)
      .implement { case (name, logLevel) =>
        LoggerService.setLoggerConfiguration(name, logLevel).mapError(_ => "Internal Error")
      }
}
