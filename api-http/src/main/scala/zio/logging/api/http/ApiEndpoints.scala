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
import zio.http.codec.{ Doc, HttpCodec, PathCodec }
import zio.http.codec.PathCodec.{ literal, string }
import zio.http.endpoint.EndpointMiddleware.None
import zio.http.endpoint._

object ApiEndpoints {
  import Domain.logLevelSchema

  def rootPathCodec(rootPath: Iterable[String]): PathCodec[Unit] =
    if (rootPath.isEmpty) {
      HttpCodec.empty
    } else {
      rootPath.map(literal).reduce(_ / _)
    }

  def getLoggerConfigurations(
    rootPath: Iterable[String] = Iterable.empty
  ): Endpoint[Unit, Domain.Error, List[Domain.LoggerConfiguration], None] =
    Endpoint
      .get(rootPathCodec(rootPath) / literal("logger"))
      .out[List[Domain.LoggerConfiguration]]
      .outErrors[Domain.Error](
        HttpCodec.error[Domain.Error.Internal](Status.InternalServerError),
        HttpCodec.error[Domain.Error.NotFound](Status.NotFound)
      )

  def getLoggerConfiguration(
    rootPath: Iterable[String] = Iterable.empty
  ): Endpoint[String, Domain.Error, Domain.LoggerConfiguration, None] =
    Endpoint
      .get(rootPathCodec(rootPath) / literal("logger") / string("name"))
      .out[Domain.LoggerConfiguration]
      .outErrors[Domain.Error](
        HttpCodec.error[Domain.Error.Internal](Status.InternalServerError),
        HttpCodec.error[Domain.Error.NotFound](Status.NotFound)
      )

  def setLoggerConfiguration(
    rootPath: Iterable[String] = Iterable.empty
  ): Endpoint[(String, LogLevel), Domain.Error, Domain.LoggerConfiguration, None] =
    Endpoint
      .put(rootPathCodec(rootPath) / literal("logger") / string("name"))
      .in[LogLevel]
      .out[Domain.LoggerConfiguration]
      .outErrors[Domain.Error](
        HttpCodec.error[Domain.Error.Internal](Status.InternalServerError),
        HttpCodec.error[Domain.Error.NotFound](Status.NotFound)
      )

  def doc(rootPath: Iterable[String] = Iterable.empty): Doc =
    getLoggerConfigurations(rootPath).doc + getLoggerConfiguration(rootPath).doc + setLoggerConfiguration(rootPath).doc
}
