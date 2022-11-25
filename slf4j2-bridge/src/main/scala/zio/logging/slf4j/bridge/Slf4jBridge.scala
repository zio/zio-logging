package zio.logging.slf4j.bridge

import org.slf4j
import zio.{ ZIO, ZLayer }

object Slf4jBridge {

  def initialize: ZLayer[Any, Nothing, Unit] =
    ZLayer {
      ZIO.runtime[Any].flatMap { runtime =>
        ZIO.succeed {
          slf4j.ZioLoggerRuntime.initialize(runtime)
        }
      }
    }
}
