package zio.logging.slf4j

import org.slf4j.impl.ZioLoggerFactory
import zio.{ ZIO, ZLayer }
import zio.logging.Logging

package object bridge {
  def bindSlf4jBridge[R <: Logging]: ZLayer[R, Nothing, R] =
    ZIO
      .runtime[R]
      .flatMap { runtime =>
        ZIO.effectTotal {
          ZioLoggerFactory.bind(runtime)
          runtime.environment
        }
      }.toLayerMany
}
