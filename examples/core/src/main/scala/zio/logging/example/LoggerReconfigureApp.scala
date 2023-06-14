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
import zio.logging.{ ConsoleLoggerConfig, LogAnnotation, LogFilter }
import zio.{ ExitCode, Runtime, Scope, ZIO, ZIOAppDefault, _ }
import zio.http._
import zio.logging.api.http.{ ApiHandlers, LoggerService }

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

//  override val bootstrap: ZLayer[ZIOAppArgs, Any, Any] =
//    Runtime.removeDefaultLoggers >>> configuredLogger(
//      for {
//        info <- Random.nextBoolean
//        cfg = Map(
//          "logger/format" -> logFormat,
//          "logger/filter/rootLevel" -> (if (info) LogLevel.Info.label else LogLevel.Debug.label)
//        )
//        _ <- Console.printLine(cfg.mkString(", ")).orDie
//        config <- ConfigProvider.fromMap(cfg, "/").nested("logger").load(ConsoleLoggerConfig.config)
//      } yield config
//    )

  class ConfigurableLogger[+Output](logger: ReconfigurableLogger[String, Output, LogFilter.LogLevelByNameConfig])
      extends ZLogger[String, Output]
      with LoggerService {

    val rootName = "root"
    override def apply(
      trace: Trace,
      fiberId: FiberId,
      logLevel: LogLevel,
      message: () => String,
      cause: Cause[Any],
      context: FiberRefs,
      spans: List[LogSpan],
      annotations: Map[String, String]
    ): Output = logger.apply(trace, fiberId, logLevel, message, cause, context, spans, annotations)

    override def getLoggerConfigs(): ZIO[Any, Throwable, List[LoggerService.LoggerConfig]] =
      ZIO.attempt {
        val currentConfig = logger.config

        LoggerService.LoggerConfig(rootName, currentConfig.rootLevel) :: currentConfig.mappings
          .map(LoggerService.LoggerConfig.tupled)
          .toList
      }

    override def getLoggerConfig(name: String): ZIO[Any, Throwable, Option[LoggerService.LoggerConfig]] =
      ZIO.attempt {
        val currentConfig = logger.config

        if (name == rootName) { Some(LoggerService.LoggerConfig(rootName, currentConfig.rootLevel)) }
        else {
          currentConfig.mappings.collectFirst {
            case (n, l) if n == name => LoggerService.LoggerConfig(n, l)
          }
        }
      }

    override def setLoggerConfig(name: String, logLevel: LogLevel): ZIO[Any, Throwable, LoggerService.LoggerConfig] =
      ZIO.attempt {
        val currentConfig = logger.config

        val newConfig = if (name == rootName) {
          currentConfig.withRootLevel(logLevel)
        } else {
          currentConfig.withMapping(name, logLevel)
        }

        logger.reconfigureIfChanged(newConfig)

        LoggerService.LoggerConfig(name, logLevel)
      }
  }

  def configuredLogger(configPath: String = "logger"): ZLayer[Any, Config.Error, Unit] =
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

                                 new ConfigurableLogger[Any](logger)
                               }

        _ <- ZIO.withLoggerScoped(logger)
      } yield ()
    }

  val configProvider: ConfigProvider = ConfigProvider.fromMap(
    Map(
      "logger/format"                              -> logFormat,
      "logger/filter/rootLevel"                    -> LogLevel.Info.label,
      "logger/filter/mappings/zio.logging.example" -> LogLevel.Debug.label
    ),
    "/"
  )

  override val bootstrap: ZLayer[Any, Config.Error, Unit] =
    Runtime.removeDefaultLoggers >>> Runtime.setConfigProvider(configProvider) >>> configuredLogger()

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

//  val loggerService = ZLayer.succeed {
//    new LoggerService {
//      override def getLoggerConfigs(): ZIO[Any, Throwable, List[LoggerService.LoggerConfig]] =
//        ZIO.succeed(LoggerService.LoggerConfig("root", LogLevel.Info) :: Nil)
//
//      override def getLoggerConfig(
//        name: String
//      ): ZIO[Any, Throwable, Option[LoggerService.LoggerConfig]] =
//        ZIO.succeed(Some(LoggerService.LoggerConfig(name, LogLevel.Info)))
//
//      override def setLoggerConfig(
//        name: String,
//        logLevel: LogLevel
//      ): ZIO[Any, Throwable, LoggerService.LoggerConfig] =
//        ZIO.succeed(LoggerService.LoggerConfig(name, logLevel))
//    }
//  }

  val loggerService: ZLayer[Any, Throwable, LoggerService] =
    ZLayer.fromZIO {
      for {
        fiberRefs <- ZIO.getFiberRefs

        loggerService <- ZIO.attempt {
                           val loggers = fiberRefs.getOrDefault(FiberRef.currentLoggers)
                           loggers.collectFirst { case logger: ConfigurableLogger[_] =>
                             logger
                           }.get
                         }

      } yield loggerService
    }

  val httpApp = ApiHandlers.routes("example" :: Nil).toApp[LoggerService]

  //    Handler
//      .html(ApiEndpoints.doc("example" :: Nil).toHtml)
//      .toHttp
//    .whenPathEq("/example")
//    .withDefaultErrorResponse

  override def run: ZIO[Scope, Any, ExitCode] =
    (for {
      _ <- Server.serve(httpApp).fork
      _ <- exec().repeat(Schedule.fixed(500.millis))
    } yield ExitCode.success).provide(loggerService ++ Server.default)

}

/*

 curl 'http://localhost:8080/example/logger'

 curl 'http://localhost:8080/example/logger/root'

 curl 'http://localhost:8080/example/logger/root'

 curl --location --request PUT 'http://localhost:8080/example/logger/root' --header 'Content-Type: application/json' --data-raw '"WARN"'

 curl --location --request PUT 'http://localhost:8080/example/logger/zio.logging.example' --header 'Content-Type: application/json' --data-raw '"WARN"'
 */
