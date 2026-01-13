/*
 * Copyright 2019-2026 John A. De Goes and the ZIO Contributors
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
import zio.{ ExitCode, Runtime, Scope, ZIO, ZIOAppDefault, _ }

import java.util.UUID

trait Tracing {
  def getCurrentSpan(): UIO[String]
}

final class LiveTracing extends Tracing {
  override def getCurrentSpan(): UIO[String] = ZIO.succeed(UUID.randomUUID().toString)
}

object LiveTracing {
  val layer: ULayer[Tracing] = ZLayer.succeed(new LiveTracing)
}

object CustomTracingAnnotationApp extends ZIOAppDefault {

  private def currentTracingSpanAspect(key: String): ZIOAspect[Nothing, Tracing, Nothing, Any, Nothing, Any] =
    new ZIOAspect[Nothing, Tracing, Nothing, Any, Nothing, Any] {
      def apply[R <: Tracing, E, A](zio: ZIO[R, E, A])(implicit trace: Trace): ZIO[R, E, A] =
        ZIO.serviceWithZIO[Tracing] { tracing =>
          tracing.getCurrentSpan().flatMap { span =>
            ZIO.logAnnotate(key, span)(zio)
          }
        }
    }

  override val bootstrap: ZLayer[ZIOAppArgs, Any, Any] = Runtime.removeDefaultLoggers >>> SLF4J.slf4j

  private val users = List.fill(2)(UUID.randomUUID())

  override def run: ZIO[Scope, Any, ExitCode] =
    (for {
      _ <- ZIO.foreachPar(users) { uId =>
             {
               ZIO.logInfo("Starting operation") *>
                 ZIO.sleep(500.millis) *>
                 ZIO.logInfo("Stopping operation")
             } @@ ZIOAspect.annotated("user", uId.toString)
           } @@ currentTracingSpanAspect("trace_id")
      _ <- ZIO.logInfo("Done")
    } yield ExitCode.success).provide(LiveTracing.layer)

}
