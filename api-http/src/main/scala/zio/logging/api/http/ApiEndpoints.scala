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
import zio.http.codec.Doc
import zio.http.codec.PathCodec.{literal, string}
import zio.http.endpoint.EndpointMiddleware.None
import zio.http.endpoint._

object ApiEndpoints {

  import Domain._

  def getLoggerConfigurations(rootPath: String): Endpoint[Unit, String, List[LoggerConfiguration], None] =
    Endpoint
      .get(literal(rootPath) / literal("logger"))
      .out[List[LoggerConfiguration]]
      .outError[String](Status.InternalServerError)

  def getLoggerConfiguration(rootPath: String): Endpoint[String, String, LoggerConfiguration, None] =
    Endpoint
      .get(literal(rootPath) / literal("logger") / string("name"))
      .out[LoggerConfiguration]
      .outError[String](Status.NotFound)
      .outError[String](Status.InternalServerError)

  def setLoggerConfiguration(rootPath: String): Endpoint[(String, LogLevel), String, LoggerConfiguration, None] =
    Endpoint
      .put(literal(rootPath) / literal("logger") / string("name"))
      .in[LogLevel]
      .out[LoggerConfiguration]
      .outError[String](Status.NotFound)
      .outError[String](Status.InternalServerError)

  def doc(rootPath: String): Doc =
    getLoggerConfigurations(rootPath).doc + getLoggerConfiguration(rootPath).doc + setLoggerConfiguration(rootPath).doc
}
