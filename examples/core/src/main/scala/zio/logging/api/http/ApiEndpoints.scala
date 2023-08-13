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

import zio._
import zio.http._
import zio.http.codec.PathCodec.{ literal, string }
import zio.http.codec.{ Doc, HttpCodec, PathCodec }
import zio.http.endpoint.EndpointMiddleware.None
import zio.http.endpoint._

object ApiEndpoints {
  import ApiDomain.logLevelSchema

  def rootPathCodec(rootPath: Seq[String]): PathCodec[Unit] =
    if (rootPath.isEmpty) {
      HttpCodec.empty
    } else {
      rootPath.map(literal).reduce(_ / _)
    }

  def getLoggerConfigs(
    rootPath: Seq[String] = Seq.empty
  ): Endpoint[Unit, ApiDomain.Error.Internal, List[ApiDomain.LoggerConfig], None] =
    Endpoint
      .get(rootPathCodec(rootPath) / literal("logger"))
      .out[List[ApiDomain.LoggerConfig]]
      .outError[ApiDomain.Error.Internal](Status.InternalServerError)

  def getLoggerConfig(
    rootPath: Seq[String] = Seq.empty
  ): Endpoint[String, ApiDomain.Error, ApiDomain.LoggerConfig, None] =
    Endpoint
      .get(rootPathCodec(rootPath) / literal("logger") / string("name"))
      .out[ApiDomain.LoggerConfig]
      .outErrors[ApiDomain.Error](
        HttpCodec.error[ApiDomain.Error.Internal](Status.InternalServerError),
        HttpCodec.error[ApiDomain.Error.NotFound](Status.NotFound)
      )

  def setLoggerConfig(
    rootPath: Seq[String] = Seq.empty
  ): Endpoint[(String, LogLevel), ApiDomain.Error.Internal, ApiDomain.LoggerConfig, None] =
    Endpoint
      .put(rootPathCodec(rootPath) / literal("logger") / string("name"))
      .in[LogLevel]
      .out[ApiDomain.LoggerConfig]
      .outError[ApiDomain.Error.Internal](Status.InternalServerError)

  def doc(rootPath: Seq[String] = Seq.empty): Doc =
    getLoggerConfigs(rootPath).doc + getLoggerConfig(rootPath).doc + setLoggerConfig(rootPath).doc
}
