package zio.logging

import zio.test._
import zio.{ Config, ConfigProvider, LogLevel }

object ConsoleLoggerConfigSpec extends ZIOSpecDefault {

  val spec: Spec[Environment, Any] = suite("ConsoleLoggerConfig")(
    test("load valid config") {

      val logPattern =
        "%highlight{%timestamp{yyyy-MM-dd'T'HH:mm:ssZ} %fixed{7}{%level} [%fiberId] %name:%line %message %cause}"

      val configProvider: ConfigProvider = ConfigProvider.fromMap(
        Map(
          "logger/pattern"                                             -> logPattern,
          "logger/filter/rootLevel"                                    -> LogLevel.Info.label,
          "logger/filter/mappings/zio.logging.example.LivePingService" -> LogLevel.Debug.label
        ),
        "/"
      )

      configProvider.load(ConsoleLoggerConfig.config.nested("logger")).map { _ =>
        assertTrue(true)
      }
    },
    test("fail on invalid config") {

      val logPattern =
        "%highlight{%timestamp{yyyy-MM-dd'T'HH:mm:ssZ} %fixed{7}{%level} [%fiberId] %name:%line %message %cause}"

      val configProvider: ConfigProvider = ConfigProvider.fromMap(
        Map(
          "logger/pattern"          -> logPattern,
          "logger/filter/rootLevel" -> "INVALID_LOG_LEVEL"
        ),
        "/"
      )

      configProvider
        .load(ConsoleLoggerConfig.config.nested("logger"))
        .exit
        .map { e =>
          assert(e)(Assertion.failsWithA[Config.Error])
        }
    }
  )
}
