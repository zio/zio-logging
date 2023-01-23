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
        debugCounter <- loggedTotalMetric.tagged(logLevelMetricLabel, LogLevel.Debug.label.toLowerCase).value
        infoCounter  <- loggedTotalMetric.tagged(logLevelMetricLabel, LogLevel.Info.label.toLowerCase).value
        warnCounter  <- loggedTotalMetric.tagged(logLevelMetricLabel, LogLevel.Warning.label.toLowerCase).value
        errorCounter <- loggedTotalMetric.tagged(logLevelMetricLabel, LogLevel.Error.label.toLowerCase).value
        fatalCounter <- loggedTotalMetric.tagged(logLevelMetricLabel, LogLevel.Fatal.label.toLowerCase).value
        clearCounter <- loggedTotalMetric.tagged(logLevelMetricLabel, LogLevel.None.label.toLowerCase).value
      } yield assertTrue(debugCounter.count == 2d) && assertTrue(infoCounter.count == 2d) && assertTrue(
        warnCounter.count == 2d
      ) && assertTrue(errorCounter.count == 1d) && assertTrue(fatalCounter.count == 0d)
        && assertTrue(clearCounter.count == 0d)).provideLayer(logMetrics)
    }
  )
}
