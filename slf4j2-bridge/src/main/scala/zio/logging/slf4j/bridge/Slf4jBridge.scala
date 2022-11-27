package zio.logging.slf4j.bridge

import zio.{ ZIO, ZLayer }

object Slf4jBridge {

  def initialize: ZLayer[Any, Nothing, Unit] =
    ZLayer {
      ZIO.runtime[Any].flatMap { runtime =>
        ZIO.succeed {
          org.slf4j.LoggerFactory
            .getILoggerFactory()
            .asInstanceOf[LoggerFactory]
            .attacheRuntime(new ZioLoggerRuntime(runtime))
        }
      }
    }
}
