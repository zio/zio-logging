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
import zio.http._
import zio.logging.api.http.ApiHandlers
import zio.logging.{ ConfigurableLogger, ConsoleLoggerConfig, LogAnnotation, LoggerConfigurer, makeConsoleLogger }
import zio.{ ExitCode, Runtime, Scope, ZIO, ZIOAppDefault, _ }

import java.util.UUID

/*
 curl -u "admin:admin" 'http://localhost:8080/example/logger'

 curl -u "admin:admin" 'http://localhost:8080/example/logger/root'

 curl -u "admin:admin" --location --request PUT 'http://localhost:8080/example/logger/root' --header 'Content-Type: application/json' --data-raw '"WARN"'

 curl -u "admin:admin" --location --request PUT 'http://localhost:8080/example/logger/zio.logging.example' --header 'Content-Type: application/json' --data-raw '"WARN"'

 */
object ConfigurableLoggerApp extends ZIOAppDefault {

  def configurableLogger(): ZLayer[Any,Config.Error,Unit] =
    ConsoleLoggerConfig
      .load()
      .flatMap { config =>
        makeConsoleLogger(config).map { logger =>
          ConfigurableLogger.make(logger, config.filter)
        }
      }
      .install

  val configProvider: ConfigProvider = TypesafeConfigProvider.fromTypesafeConfig(ConfigFactory.load("logger.conf"))

  override val bootstrap: ZLayer[Any, Config.Error, Unit] =
    Runtime.removeDefaultLoggers >>> Runtime.setConfigProvider(configProvider) >>> configurableLogger()

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

  val httpApp: HttpApp[LoggerConfigurer] =
    ApiHandlers.routes("example" :: Nil).toHttpApp @@ Middleware.basicAuth("admin", "admin")

  override def run: ZIO[Scope, Any, ExitCode] =
    (for {
      _ <- Server.serve(httpApp).fork
      _ <- exec().repeat(Schedule.fixed(500.millis))
    } yield ExitCode.success).provide(LoggerConfigurer.layer ++ Server.default)

}
