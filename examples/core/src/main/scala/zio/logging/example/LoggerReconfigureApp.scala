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
package zio.logging.example

import com.typesafe.config.ConfigFactory
import zio.config.typesafe.TypesafeConfigProvider
import zio.logging.{ ConsoleLoggerConfig, LogAnnotation, ReconfigurableLogger, _ }
import zio.{ Config, ExitCode, Runtime, Scope, ZIO, ZIOAppDefault, _ }

import java.util.UUID

object LoggerReconfigureApp extends ZIOAppDefault {

  def configuredLogger(
    loadConfig: => ZIO[Any, Config.Error, ConsoleLoggerConfig]
  ): ZLayer[Any, Config.Error, Unit] =
    ReconfigurableLogger
      .make[Any, Config.Error, String, Any, ConsoleLoggerConfig](
        loadConfig,
        (config, _) => makeConsoleLogger(config),
        Schedule.fixed(500.millis)
      )
      .installUnscoped

  override val bootstrap: ZLayer[ZIOAppArgs, Any, Any] =
    Runtime.removeDefaultLoggers >>> configuredLogger(
      for {
        config       <- ZIO.succeed(ConfigFactory.load("logger.conf"))
        _            <- Console.printLine(config.getConfig("logger")).orDie
        loggerConfig <- ConsoleLoggerConfig.load().withConfigProvider(TypesafeConfigProvider.fromTypesafeConfig(config))
      } yield loggerConfig
    )

  def exec(): ZIO[Any, Nothing, Unit] =
    for {
      ok      <- Random.nextBoolean
      traceId <- ZIO.succeed(UUID.randomUUID())
      _       <- ZIO.logDebug("Start") @@ LogAnnotation.TraceId(traceId)
      userIds <- ZIO.succeed(List.fill(2)(UUID.randomUUID().toString))
      _       <- ZIO.foreachPar(userIds) { userId =>
                   {
                     ZIO.logDebug("Starting operation") *>
                       ZIO.logInfo("OK operation").when(ok) *>
                       ZIO.logError("Error operation").when(!ok) *>
                       ZIO.logDebug("Stopping operation")
                   } @@ LogAnnotation.UserId(userId)
                 } @@ LogAnnotation.TraceId(traceId)
      _       <- ZIO.logDebug("Done") @@ LogAnnotation.TraceId(traceId)
    } yield ()

  override def run: ZIO[Scope, Any, ExitCode] =
    for {
      _ <- exec().repeat(Schedule.fixed(500.millis))
    } yield ExitCode.success

}
