package zio.logging

import zio.logging.test.TestService
import zio.test._
import zio.{ LogLevel, ZIO }

object MetricsSpec extends ZIOSpecDefault {

  val spec: Spec[Environment, Any] = suite("MetricsSpec")(
    test("logs totals metrics") {
      (for {
        _            <- ZIO.logDebug("debug")
        _            <- ZIO.logInfo("info")
        _            <- ZIO.logWarning("warning")
        _            <- TestService.testDebug
        _            <- TestService.testInfo
        _            <- TestService.testWarning
        _            <- TestService.testError
        debugCounter <- loggedTotalMetrics(LogLevel.Debug).value
        infoCounter  <- loggedTotalMetrics(LogLevel.Info).value
        warnCounter  <- loggedTotalMetrics(LogLevel.Warning).value
        errorCounter <- loggedTotalMetrics(LogLevel.Error).value
        fatalCounter <- loggedTotalMetrics(LogLevel.Fatal).value
      } yield assertTrue(debugCounter.count == 2) && assertTrue(infoCounter.count == 2) && assertTrue(
        warnCounter.count == 2
      ) && assertTrue(errorCounter.count == 1) && assertTrue(fatalCounter.count == 0)).provideLayer(metrics)
    }
  )
}
