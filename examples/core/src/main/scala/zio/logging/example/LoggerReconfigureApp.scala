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

import zio.http.HttpAppMiddleware.basicAuth
import zio.logging.internal.ReconfigurableLogger
import zio.logging.{ ConfigurableLogger, ConsoleLoggerConfig, LogAnnotation, LogFilter, LoggerConfigurer }
import zio.{ ExitCode, Runtime, Scope, ZIO, ZIOAppDefault, _ }
import zio.http._
import zio.logging.api.http.ApiHandlers

import java.util.UUID

object LoggerReconfigureApp extends ZIOAppDefault {

  class Configurer(logger: ReconfigurableLogger[_, _, LogFilter.LogLevelByNameConfig]) extends LoggerConfigurer {

    val rootName = "root"

    override def getLoggerConfigs(): ZIO[Any, Throwable, List[LoggerConfigurer.LoggerConfig]] =
      ZIO.attempt {
        val currentConfig = logger.config

        LoggerConfigurer.LoggerConfig(rootName, currentConfig.rootLevel) :: currentConfig.mappings
          .map(LoggerConfigurer.LoggerConfig.tupled)
          .toList
      }

    override def getLoggerConfig(name: String): ZIO[Any, Throwable, Option[LoggerConfigurer.LoggerConfig]] =
      ZIO.attempt {
        val currentConfig = logger.config

        if (name == rootName) {
          Some(LoggerConfigurer.LoggerConfig(rootName, currentConfig.rootLevel))
        } else {
          currentConfig.mappings.collectFirst {
            case (n, l) if n == name => LoggerConfigurer.LoggerConfig(n, l)
          }
        }
      }

    override def setLoggerConfig(name: String, logLevel: LogLevel): ZIO[Any, Throwable, LoggerConfigurer.LoggerConfig] =
      ZIO.attempt {
        val currentConfig = logger.config

        val newConfig = if (name == rootName) {
          currentConfig.withRootLevel(logLevel)
        } else {
          currentConfig.withMapping(name, logLevel)
        }

        logger.reconfigureIfChanged(newConfig)

        LoggerConfigurer.LoggerConfig(name, logLevel)
      }
  }

  def configurableLogger(configPath: String = "logger"): ZLayer[Any, Config.Error, Unit] =
    ZLayer.scoped {
      for {
        consoleLoggerConfig <- ZIO.config(ConsoleLoggerConfig.config.nested(configPath))
        filterConfig        <- ZIO.succeed {
                                 consoleLoggerConfig.filter
                                   .asInstanceOf[LogFilter.ConfiguredFilter[String, LogFilter.LogLevelByNameConfig]]
                                   .config
                               }
        logger              <- ZIO.succeed {
                                 val logger = ReconfigurableLogger[String, Any, LogFilter.LogLevelByNameConfig](
                                   filterConfig,
                                   config => {
                                     val filter = LogFilter.logLevelByName(config)

                                     filter.filter(consoleLoggerConfig.format.toLogger.map { line =>
                                       try java.lang.System.out.println(line)
                                       catch {
                                         case t: VirtualMachineError => throw t
                                         case _: Throwable           => ()
                                       }
                                     })
                                   }
                                 )

                                 ConfigurableLogger(logger, new Configurer(logger))
                               }

        _ <- ZIO.withLoggerScoped(logger)
      } yield ()
    }

  val logFormat =
    "%highlight{%timestamp{yyyy-MM-dd'T'HH:mm:ssZ} %fixed{7}{%level} [%fiberId] %name:%line %message %kv{trace_id} %kv{user_id} %cause}"

  val configProvider: ConfigProvider = ConfigProvider.fromMap(
    Map(
      "logger/format"                              -> logFormat,
      "logger/filter/rootLevel"                    -> LogLevel.Info.label,
      "logger/filter/mappings/zio.logging.example" -> LogLevel.Debug.label
    ),
    "/"
  )

  override val bootstrap: ZLayer[Any, Config.Error, Unit] =
    Runtime.removeDefaultLoggers >>> Runtime.setConfigProvider(configProvider) >>> configurableLogger()

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

  val httpApp = ApiHandlers.routes("example" :: Nil).toApp[LoggerConfigurer] @@ basicAuth("admin", "admin")

  override def run: ZIO[Scope, Any, ExitCode] =
    (for {
      _ <- Server.serve(httpApp).fork
      _ <- exec().repeat(Schedule.fixed(500.millis))
    } yield ExitCode.success).provide(LoggerConfigurer.layer ++ Server.default)

}

/*

 curl -u "admin:admin" 'http://localhost:8080/example/logger'

 curl -u "admin:admin" 'http://localhost:8080/example/logger/root'

 curl -u "admin:admin" 'http://localhost:8080/example/logger/root'

 curl -u "admin:admin" --location --request PUT 'http://localhost:8080/example/logger/root' --header 'Content-Type: application/json' --data-raw '"WARN"'

 curl -u "admin:admin" --location --request PUT 'http://localhost:8080/example/logger/zio.logging.example' --header 'Content-Type: application/json' --data-raw '"WARN"'

 */
