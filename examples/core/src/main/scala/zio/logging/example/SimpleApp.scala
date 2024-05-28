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

import zio.logging.{ ConsoleLoggerConfig, consoleLogger }
import zio.{ ExitCode, Runtime, Scope, ZIO, ZIOAppArgs, ZIOAppDefault, ZLayer }

object SimpleApp extends ZIOAppDefault {

  override val bootstrap: ZLayer[ZIOAppArgs, Any, Any] =
    Runtime.removeDefaultLoggers >>> consoleLogger(ConsoleLoggerConfig.default)

  override def run: ZIO[Scope, Any, ExitCode] = {
    val ee: ZIO[Any, String, Unit] = ZIO.die(new Exception(""))
    for {
      _ <- ZIO.logInfo("Start")
      _ <- ee.catchAll(c => ZIO.logError(s"error $c"))
      _ <- ZIO.logInfo("Done")
    } yield ExitCode.success
  }

}
