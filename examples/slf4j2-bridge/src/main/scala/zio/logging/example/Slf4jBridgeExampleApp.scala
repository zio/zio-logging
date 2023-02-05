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

import zio.logging.slf4j.bridge.Slf4jBridge
import zio.logging.{ LogFilter, LogFormat, LoggerNameExtractor, consoleJson }
import zio.{ ExitCode, LogLevel, Runtime, Scope, ZIO, ZIOAppArgs, ZIOAppDefault, ZLayer }

object Slf4jBridgeExampleApp extends ZIOAppDefault {

  private val slf4jLogger = org.slf4j.LoggerFactory.getLogger("SLF4J-LOGGER")

  private val logFilter: LogFilter[String] = LogFilter.logLevelByName(
    LogLevel.Info,
    "zio.logging.slf4j" -> LogLevel.Debug,
    "SLF4J-LOGGER"      -> LogLevel.Warning
  )

  override val bootstrap: ZLayer[ZIOAppArgs, Any, Any] =
    Runtime.removeDefaultLoggers >>> consoleJson(
      LogFormat.label("name", LoggerNameExtractor.loggerNameAnnotationOrTrace.toLogFormat()) + LogFormat.default,
      logFilter
    ) >+> Slf4jBridge.initialize

  override def run: ZIO[Scope, Any, ExitCode] =
    for {
      _ <- ZIO.logDebug("Start")
      _ <- ZIO.succeed(slf4jLogger.debug("Test {}!", "DEBUG"))
      _ <- ZIO.succeed(slf4jLogger.warn("Test {}!", "WARNING"))
      _ <- ZIO.logInfo("Done")
    } yield ExitCode.success

}
