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
package zio.logging.api.http

import zio.http.{ Handler, Route, Routes }
import zio.logging.LoggerConfigurer
import zio.logging.api.http.ApiDomain.Error
import zio.{ LogLevel, ZIO }

object ApiHandlers {

  def getLoggerConfigs(rootPath: Seq[String] = Seq.empty): Route[LoggerConfigurer, Nothing] =
    ApiEndpoints
      .getLoggerConfigs(rootPath)
      .implement {
        Handler.fromFunctionZIO[Unit] { _ =>
          LoggerConfigurer
            .getLoggerConfigs()
            .map(_.map(ApiDomain.LoggerConfig.from))
            .mapError(_ => Error.Internal())
        }
      }

  def getLoggerConfig(rootPath: Seq[String] = Seq.empty): Route[LoggerConfigurer, Nothing] =
    ApiEndpoints
      .getLoggerConfig(rootPath)
      .implement {
        Handler.fromFunctionZIO[String] { name =>
          LoggerConfigurer.getLoggerConfig(name).mapError(_ => Error.Internal()).flatMap {
            case Some(r) => ZIO.succeed(ApiDomain.LoggerConfig.from(r))
            case _       => ZIO.fail(Error.NotFound())
          }
        }
      }

  def setLoggerConfigs(rootPath: Seq[String] = Seq.empty): Route[LoggerConfigurer, Nothing] =
    ApiEndpoints
      .setLoggerConfig(rootPath)
      .implement {
        Handler.fromFunctionZIO[(String, LogLevel)] { case (name, logLevel) =>
          LoggerConfigurer
            .setLoggerConfig(name, logLevel)
            .map(ApiDomain.LoggerConfig.from)
            .mapError(_ => Error.Internal())
        }
      }

  def routes(rootPath: Seq[String] = Seq.empty): Routes[LoggerConfigurer, Nothing] =
    Routes(getLoggerConfigs(rootPath), getLoggerConfig(rootPath), setLoggerConfigs(rootPath))
}
