package zio.logging

import zio.test._
import zio.{ Config, ConfigProvider, LogLevel }

import java.net.URI
import java.nio.charset.StandardCharsets
import java.nio.file.Paths

object FileLoggerConfigSpec extends ZIOSpecDefault {

  val spec: Spec[Environment, Any] = suite("FileLoggerConfig")(
    test("load valid config") {

      val logPattern =
        "%highlight{%timestamp{yyyy-MM-dd'T'HH:mm:ssZ} %fixed{7}{%level} [%fiberId] %name:%line %message %cause}"

      val configProvider: ConfigProvider = ConfigProvider.fromMap(
        Map(
          "logger/pattern"                                             -> logPattern,
          "logger/path"                                                -> "file:///tmp/test.log",
          "logger/autoFlushBatchSize"                                  -> "2",
          "logger/bufferedIOSize"                                      -> "4096",
          "logger/filter/rootLevel"                                    -> LogLevel.Info.label,
          "logger/filter/mappings/zio.logging.example.LivePingService" -> LogLevel.Debug.label
        ),
        "/"
      )

      configProvider.load(FileLoggerConfig.config.nested("logger")).map { loadedConfig =>
        assertTrue(loadedConfig.charset == StandardCharsets.UTF_8) &&
        assertTrue(loadedConfig.destination == Paths.get(URI.create("file:///tmp/test.log"))) &&
        assertTrue(loadedConfig.autoFlushBatchSize == 2) &&
        assertTrue(loadedConfig.bufferedIOSize == Some(4096))
      }
    },
    test("load default config") {

      val configProvider: ConfigProvider = ConfigProvider.fromMap(
        Map(
          "logger/path" -> "file:///tmp/test.log"
        ),
        "/"
      )

      configProvider.load(FileLoggerConfig.config.nested("logger")).map { loadedConfig =>
        assertTrue(loadedConfig.charset == StandardCharsets.UTF_8) &&
        assertTrue(loadedConfig.destination == Paths.get(URI.create("file:///tmp/test.log"))) &&
        assertTrue(loadedConfig.autoFlushBatchSize == 1) &&
        assertTrue(loadedConfig.bufferedIOSize.isEmpty)
      }
    },
    test("fail on invalid charset and filter config") {
      val logPattern =
        "%highlight{%timestamp{yyyy-MM-dd'T'HH:mm:ssZ} %fixed{7}{%level} [%fiberId] %name:%line %message %cause}"

      val configProvider: ConfigProvider = ConfigProvider.fromMap(
        Map(
          "logger/pattern"          -> logPattern,
          "logger/charset"          -> "INVALID_CHARSET",
          "logger/filter/rootLevel" -> "INVALID_LOG_LEVEL"
        ),
        "/"
      )

      configProvider
        .load(FileLoggerConfig.config.nested("logger"))
        .exit
        .map { e =>
          assert(e)(Assertion.failsWithA[Config.Error])
        }
    },
    test("fail on invalid pattern config") {
      val logPattern =
        "%highlight{%timestamp{yyyy-MM-dd'T'HH:mm:ssZ} %fixed{%level} [%fiberId] %name:%line %message %cause}"

      val configProvider: ConfigProvider = ConfigProvider.fromMap(
        Map(
          "logger/pattern" -> logPattern,
          "logger/path"    -> "file:///tmp/test.log"
        ),
        "/"
      )

      configProvider
        .load(FileLoggerConfig.config.nested("logger"))
        .exit
        .map { e =>
          assert(e)(Assertion.failsWithA[Config.Error])
        }
    }
  )
}
