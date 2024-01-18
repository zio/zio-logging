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

import zio.logging.backend.SLF4J
import zio.{ ExitCode, Runtime, Scope, URIO, ZIO, ZIOAppArgs, ZIOAppDefault, ZLayer }

object Slf4jExampleApp extends ZIOAppDefault {

  override val bootstrap: ZLayer[ZIOAppArgs, Any, Any] = Runtime.removeDefaultLoggers >>> SLF4J.slf4j

  private def ping(address: String): URIO[PingService, Unit] =
    PingService
      .ping(address)
      .tap(result => ZIO.logInfo(s"ping: $address - result: $result"))
      .tapErrorCause(error => ZIO.logErrorCause(s"ping: $address - error", error))
      .unit
      .ignore

  override def run: ZIO[Scope, Any, ExitCode] =
    (for {
      _ <- ping("127.0.0.1")
      _ <- ping("x8.8.8.8")
    } yield ExitCode.success).provide(LivePingService.layer)

}
