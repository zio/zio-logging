package zio.logging.slf4j.bridge

import org.slf4j.impl.ZioLoggerFactory
import zio.{ EnvironmentTag, ZEnv, ZIO, ZIOApp, ZIOAppArgs, ZLayer }

trait Slf4jBridge extends ZIOApp {
  override type Environment = ZIOAppArgs
  override implicit def tag: EnvironmentTag[ZIOAppArgs] = Slf4jBridge.envTag

  override def layer: ZLayer[ZIOAppArgs, Any, ZIOAppArgs] = ZLayer.environment[ZIOAppArgs]

  override def run: ZIO[ZEnv with ZIOAppArgs, Any, Any] =
    ZIO.runtime[Any].flatMap { runtime =>
      ZIO.succeed {
        ZioLoggerFactory.initialize(runtime)
        runtime.environment
      }
    }
}

object Slf4jBridge extends Slf4jBridge {
  private val envTag: EnvironmentTag[ZIOAppArgs] = EnvironmentTag[ZIOAppArgs]
}
