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

import com.typesafe.config.ConfigFactory
import zio.logging.Logger
import zio.{ Cause, Config, ConfigProvider, ExitCode, LogLevel, Runtime, Scope, URIO, ZIO, ZIOAppDefault, ZLayer }

object ConsoleColoredApp extends ZIOAppDefault {

  val logPattern = "%timestamp{yyyy-MM-dd'T'HH:mm:ssZ} %highlight{%level [%fiberId] %name:%line %message %cause}"

  val configProvider: ConfigProvider = ConfigProvider.fromMap(
    Map(
      "logger/pattern"                                             -> logPattern,
      "logger/path"                                                -> "file:///tmp/console_app.log",
      "logger/filter/rootLevel"                                    -> LogLevel.Info.label,
      "logger/filter/mappings/zio.logging.example.LivePingService" -> LogLevel.Debug.label
    ),
    "/"
  )

//  val configProvider: ConfigProvider = zio.config.typesafe.TypesafeConfigProvider.fromResourcePath

  override val bootstrap: ZLayer[Any, Config.Error, Unit] =
    Runtime.removeDefaultLoggers >>> Runtime
      .setConfigProvider(configProvider) >>> (Logger.consoleLogger() ++ Logger.fileAsyncStringLogger())

  private def ping(address: String): URIO[PingService, Unit] =
    PingService
      .ping(address)
      .foldZIO(
        e => ZIO.logErrorCause(s"ping: $address - error", Cause.fail(e)),
        r => ZIO.logInfo(s"ping: $address - result: $r")
      )

  override def run: ZIO[Scope, Any, ExitCode] =
    (for {
      _ <- ping("127.0.0.1")
      _ <- ping("x8.8.8.8")
    } yield ExitCode.success).provide(LivePingService.layer)

}
