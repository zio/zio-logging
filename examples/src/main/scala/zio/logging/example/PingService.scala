/*
 * Copyright 2019-2022 John A. De Goes and the ZIO Contributors
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

import zio.{ Task, ULayer, ZIO, ZLayer }

import java.net.InetAddress

trait PingService {
  def ping(address: String): Task[Boolean]
}

object PingService {
  def ping(address: String): ZIO[PingService, Throwable, Boolean] = ZIO.serviceWithZIO[PingService](_.ping(address))
}

final class LivePingService extends PingService {
  override def ping(address: String): Task[Boolean] =
    for {
      inetAddress <-
        ZIO
          .attempt(InetAddress.getByName(address))
          .tapErrorCause(error => ZIO.logErrorCause(s"ping: $address - invalid address error", error))
      _           <- ZIO.logDebug(s"ping: $inetAddress")
      result      <- ZIO.attempt(inetAddress.isReachable(10000))
    } yield result
}

object LivePingService {
  val layer: ULayer[PingService] = ZLayer.succeed(new LivePingService)
}
