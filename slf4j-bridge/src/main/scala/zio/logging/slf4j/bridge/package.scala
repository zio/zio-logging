package zio.logging.slf4j

import org.slf4j.impl.ZioLoggerFactory
import zio.logging.Logging
import zio.{ Tag, ZIO, ZLayer }

package object bridge {
  def initializeSlf4jBridge[R <: Logging: Tag]: ZLayer[R, Nothing, R] =
    ZIO
      .runtime[R]
      .flatMap { runtime =>
        ZIO.succeed {
          ZioLoggerFactory.initialize(runtime)
          runtime.environment
        }
      }
      .toLayerMany
}
