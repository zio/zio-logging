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
package zio.logging.example

import zio.logging.{ LogAnnotation, consoleJsonLogger }
import zio.{ ExitCode, Runtime, Scope, ZIO, ZIOAppDefault, _ }

import java.util.UUID

object ConsoleJsonApp extends ZIOAppDefault {

  final case class User(firstName: String, lastName: String) {
    def toJson: String = s"""{"first_name":"$firstName","last_name":"$lastName"}""".stripMargin
  }

  private val userLogAnnotation = LogAnnotation[User]("user", (_, u) => u, _.toJson)
  private val uuid              = LogAnnotation[UUID]("uuid", (_, i) => i, _.toString)

  val logFormat =
    "%label{timestamp}{%timestamp{yyyy-MM-dd'T'HH:mm:ssZ}} %label{level}{%level} %label{fiberId}{%fiberId} %label{message}{%message} %label{cause}{%cause} %label{name}{%name} %kvs"

  val configProvider: ConfigProvider = ConfigProvider.fromMap(
    Map(
      "logger/format"           -> logFormat,
      "logger/filter/rootLevel" -> LogLevel.Info.label
    ),
    "/"
  )

  override val bootstrap: ZLayer[ZIOAppArgs, Any, Any] =
    Runtime.removeDefaultLoggers >>> Runtime.setConfigProvider(configProvider) >>> consoleJsonLogger()

  private val uuids = List.fill(2)(UUID.randomUUID())

  override def run: ZIO[Scope, Any, ExitCode] =
    for {
      traceId <- ZIO.succeed(UUID.randomUUID())
      _       <- ZIO.foreachPar(uuids) { uId =>
                   {
                     ZIO.logInfo("Starting operation") *>
                       ZIO.sleep(500.millis) *>
                       ZIO.logInfo("Stopping operation")
                   } @@ userLogAnnotation(User("John", "Doe")) @@ uuid(uId)
                 } @@ LogAnnotation.TraceId(traceId)
      _       <- ZIO.logInfo("Done")
    } yield ExitCode.success

}
