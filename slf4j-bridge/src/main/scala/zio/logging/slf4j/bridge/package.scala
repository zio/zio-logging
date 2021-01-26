package zio.logging.slf4j

import org.slf4j.impl.ZioLoggerFactory
import zio.{ ZIO, ZLayer }
import zio.logging.Logging

package object bridge {
  def initializeSlf4jBridge[R <: Logging]: ZLayer[R, Nothing, R] =
    ZIO
      .runtime[R]
      .flatMap { runtime =>
        ZIO.effectTotal {
          ZioLoggerFactory.initialize(runtime)
          runtime.environment
        }
      }
      .toLayerMany
}
