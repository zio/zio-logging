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
        pingResult.isLeft && pingResult.swap.exists(_.isInstanceOf[UnknownHostException])
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
