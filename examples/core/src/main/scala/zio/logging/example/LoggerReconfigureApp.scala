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

import zio.logging.internal.ReconfigurableLogger
import zio.logging.{ ConsoleLoggerConfig, LogAnnotation }
import zio.{ ExitCode, Runtime, Scope, ZIO, ZIOAppDefault, _ }
import zio.http.Server
import zio.logging.api.http.{ ApiHandlers, ApiDomain, LoggerService }

import java.util.UUID

object LoggerReconfigureApp extends ZIOAppDefault {

  val logFormat =
    "%highlight{%timestamp{yyyy-MM-dd'T'HH:mm:ssZ} %fixed{7}{%level} [%fiberId] %name:%line %message %kv{trace_id} %kv{user_id} %cause}"

  def configuredLogger(
    loadConfig: => ZIO[Any, Config.Error, ConsoleLoggerConfig]
  ): ZLayer[Any, Config.Error, Unit] =
    ZLayer.scoped {
      for {
        logger <- ReconfigurableLogger
                    .make[Config.Error, String, Any, ConsoleLoggerConfig](
                      loadConfig,
                      config =>
                        config.filter.filter(config.format.toLogger.map { line =>
                          try java.lang.System.out.println(line)
                          catch {
                            case t: VirtualMachineError => throw t
                            case _: Throwable           => ()
                          }
                        }),
                      500.millis
                    )
        _      <- ZIO.withLoggerScoped(logger)
      } yield ()
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

  def exec() =
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

  val loggerService = ZLayer.succeed {
    new LoggerService {
      override def getLoggerConfigs(): ZIO[Any, Throwable, List[LoggerService.LoggerConfig]] =
        ZIO.succeed(LoggerService.LoggerConfig("root", LogLevel.Info) :: Nil)

      override def getLoggerConfig(
        name: String
      ): ZIO[Any, Throwable, Option[LoggerService.LoggerConfig]] =
        ZIO.succeed(Some(LoggerService.LoggerConfig(name, LogLevel.Info)))

      override def setLoggerConfig(
        name: String,
        logLevel: LogLevel
      ): ZIO[Any, Throwable, LoggerService.LoggerConfig] =
        ZIO.succeed(LoggerService.LoggerConfig(name, logLevel))
    }
  }

  val httpApp = ApiHandlers.routes("example" :: Nil).toApp[LoggerService]

  override def run: ZIO[Scope, Any, ExitCode] =
    (for {
      _ <- Server.serve(httpApp).fork
      _ <- exec().repeat(Schedule.fixed(500.millis))
    } yield ExitCode.success).provide(loggerService ++ Server.default)

}
