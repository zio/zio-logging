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

import zio.test.{ Spec, TestAspect, TestEnvironment, ZIOSpecDefault, ZTestLogger, assertTrue }
import zio.{ LogLevel, Runtime }

import java.net.UnknownHostException

object PingServiceSpec extends ZIOSpecDefault {

  override def spec: Spec[TestEnvironment, Any] = suite("PingServiceSpec")(
    test("ping with valid address") {
      for {
        pingResult   <- PingService.ping("127.0.0.1")
        loggerOutput <- ZTestLogger.logOutput
      } yield assertTrue(pingResult) && assertTrue(loggerOutput.size == 1) && assertTrue(
        loggerOutput(0).logLevel == LogLevel.Debug && loggerOutput(0).message() == "ping: /127.0.0.1"
      )
    },
    test("fail on invalid address") {
      for {
        pingResult   <- PingService.ping("x8.8.8.8").either
        loggerOutput <- ZTestLogger.logOutput
      } yield assertTrue(
        pingResult.isLeft && pingResult.fold(_.isInstanceOf[UnknownHostException], _ => false)
      ) && assertTrue(loggerOutput.size == 1) && assertTrue(
        loggerOutput(0).logLevel == LogLevel.Error && loggerOutput(0)
          .message() == "ping: x8.8.8.8 - invalid address error" && loggerOutput(0).cause.failureOption.exists(
          _.isInstanceOf[UnknownHostException]
        )
      )
    }
  ).provideLayer(
    LivePingService.layer ++ (Runtime.removeDefaultLoggers >>> ZTestLogger.default)
  ) @@ TestAspect.sequential
}
