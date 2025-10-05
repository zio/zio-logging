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

import zio._
import zio.http._
import zio.http.codec.PathCodec.{ literal, string }
import zio.http.codec.{ HttpCodec, PathCodec }
import zio.http.endpoint.AuthType.None
import zio.http.endpoint._
import zio.http.endpoint.openapi.{ OpenAPI, OpenAPIGen }
import zio.logging.api.http.ApiDomain.Error

object ApiEndpoints {
  import ApiDomain.logLevelSchema

  def rootPathCodec(rootPath: Seq[String]): PathCodec[Unit] =
    if (rootPath.isEmpty) {
      PathCodec.empty
    } else {
      rootPath.map(literal).reduce(_ / _)
    }

  def getLoggerConfigs(
    rootPath: Seq[String] = Seq.empty
  ): Endpoint[Unit, Unit, Error.Internal, List[ApiDomain.LoggerConfig], None] =
    Endpoint(Method.GET / rootPathCodec(rootPath) / literal("logger"))
      .out[List[ApiDomain.LoggerConfig]]
      .outError[ApiDomain.Error.Internal](Status.InternalServerError)

  def getLoggerConfig(
    rootPath: Seq[String] = Seq.empty
  ): Endpoint[String, String, Error, ApiDomain.LoggerConfig, None] =
    Endpoint(Method.GET / rootPathCodec(rootPath) / literal("logger") / string("name"))
      .out[ApiDomain.LoggerConfig]
      .outErrors[ApiDomain.Error](
        HttpCodec.error[ApiDomain.Error.Internal](Status.InternalServerError),
        HttpCodec.error[ApiDomain.Error.NotFound](Status.NotFound)
      )

  def setLoggerConfig(
    rootPath: Seq[String] = Seq.empty
  ): Endpoint[String, (String, LogLevel), Error.Internal, ApiDomain.LoggerConfig, None] =
    Endpoint(Method.PUT / rootPathCodec(rootPath) / literal("logger") / string("name"))
      .in[LogLevel]
      .out[ApiDomain.LoggerConfig]
      .outError[ApiDomain.Error.Internal](Status.InternalServerError)

  def openAPI(rootPath: Seq[String] = Seq.empty): OpenAPI =
    OpenAPIGen.fromEndpoints(
      title = "Logger Configurations API",
      version = "1.0",
      getLoggerConfigs(rootPath),
      getLoggerConfig(rootPath),
      setLoggerConfig(rootPath)
    )
}
