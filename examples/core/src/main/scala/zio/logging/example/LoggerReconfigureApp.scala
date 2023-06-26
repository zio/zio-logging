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

import zio.logging.internal.ReconfigurableLogger2
import zio.logging.{ ConfigurableLogger, ConsoleLoggerConfig, LogAnnotation, LogFilter, LoggerConfigurer, LoggerLayers }
import zio.{ Config, ExitCode, Runtime, Scope, ZIO, ZIOAppDefault, _ }

import java.util.UUID

object LoggerReconfigureApp extends ZIOAppDefault {

  val logFormat =
    "%highlight{%timestamp{yyyy-MM-dd'T'HH:mm:ssZ} %fixed{7}{%level} [%fiberId] %name:%line %message %kv{trace_id} %kv{user_id} %cause}"

  def configuredLogger(
    loadConfig: => ZIO[Any, Config.Error, ConsoleLoggerConfig]
  ) = {
    import LoggerLayers._
    ZLayer.scoped {
      ReconfigurableLogger2
        .make[Config.Error, String, Any, ConsoleLoggerConfig](
          loadConfig,
          (config, _) =>
            makeSystemOutLogger(config.format.toLogger).map { logger =>
              config.filter.filter(logger)
            },
          Schedule.fixed(500.millis)
        )
        .flatMap(logger => ZIO.withLoggerScoped(logger))
    }
  }

  override val bootstrap: ZLayer[ZIOAppArgs, Any, Any] =
    Runtime.removeDefaultLoggers >>> configuredLogger(
      for {
        info   <- Random.nextBoolean
        cfg     = Map(
                    "logger/format"           -> logFormat,
                    "logger/filter/rootLevel" -> (if (info) LogLevel.Info.label else LogLevel.Debug.label)
                  )
        _      <- Console.printLine(cfg.mkString(", ")).orDie
        config <- ConfigProvider.fromMap(cfg, "/").nested("logger").load(ConsoleLoggerConfig.config)
      } yield config
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
