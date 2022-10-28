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

import zio.logging.{ LogAnnotation, logContext }
import zio.test.Assertion._
import zio.test._
import zio.{ Chunk, LogLevel, Runtime, ZIO, ZIOAspect, _ }

import java.util.UUID

object LoggingSpec extends ZIOSpecDefault {

  override def spec: Spec[TestEnvironment, Any] = suite("LoggingSpec")(
    test("start stop log output") {
      val users = Chunk.fill(2)(UUID.randomUUID())
      for {
        traceId      <- ZIO.succeed(UUID.randomUUID())
        _            <- ZIO.foreach(users) { uId =>
                          {
                            ZIO.logInfo("Starting operation") *> ZIO.sleep(500.millis) *> ZIO.logInfo("Stopping operation")
                          } @@ ZIOAspect.annotated("user", uId.toString)
                        } @@ LogAnnotation.TraceId(traceId)
        _            <- ZIO.logInfo("Done")
        loggerOutput <- ZTestLogger.logOutput
      } yield assertTrue(loggerOutput.size == 5) && assertTrue(
        loggerOutput.forall(_.logLevel == LogLevel.Info)
      ) && assert(loggerOutput.map(_.message()))(
        equalTo(
          Chunk(
            "Starting operation",
            "Stopping operation",
            "Starting operation",
            "Stopping operation",
            "Done"
          )
        )
      ) && assert(loggerOutput.map(_.context.get(logContext).flatMap(_.asMap.get(LogAnnotation.TraceId.name))))(
        equalTo(
          Chunk.fill(4)(Some(traceId.toString)) :+ None
        )
      ) && assert(loggerOutput.map(_.annotations.get("user")))(
        equalTo(users.flatMap(u => Chunk.fill(2)(Some(u.toString))) :+ None)
      )
    }
  ).provideLayer(
    Runtime.removeDefaultLoggers >>> ZTestLogger.default
  ) @@ TestAspect.withLiveClock
}
