package zio.logging

import zio.test._
import zio.{ Config, ConfigProvider, LogLevel }

import java.net.URI
import java.nio.charset.StandardCharsets
import java.nio.file.Paths

object FileLoggerConfigSpec extends ZIOSpecDefault {

  val spec: Spec[Environment, Any] = suite("FileLoggerConfig")(
    test("load valid config") {

      val logFormat =
        "%highlight{%timestamp{yyyy-MM-dd'T'HH:mm:ssZ} %fixed{7}{%level} [%fiberId] %name:%line %message %cause}"

      val configProvider: ConfigProvider = ConfigProvider.fromMap(
        Map(
          "logger/format"                                              -> logFormat,
          "logger/path"                                                -> "file:///tmp/test.log",
          "logger/autoFlushBatchSize"                                  -> "2",
          "logger/bufferedIOSize"                                      -> "4096",
          "logger/rollingPolicy/type"                                  -> "TimeBasedRollingPolicy",
          "logger/filter/rootLevel"                                    -> LogLevel.Info.label,
          "logger/filter/mappings/zio.logging.example.LivePingService" -> LogLevel.Debug.label
        ),
        "/"
      )

      configProvider.load(FileLoggerConfig.config.nested("logger")).map { loadedConfig =>
        assertTrue(loadedConfig.charset == StandardCharsets.UTF_8) &&
        assertTrue(loadedConfig.destination == Paths.get(URI.create("file:///tmp/test.log"))) &&
        assertTrue(loadedConfig.autoFlushBatchSize == 2) &&
        assertTrue(loadedConfig.bufferedIOSize == Some(4096)) &&
        assertTrue(loadedConfig.rollingPolicy == Some(FileLoggerConfig.FileRollingPolicy.TimeBasedRollingPolicy))
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
    test("equals config with same sources") {

      val logFormat =
        "%highlight{%timestamp %fixed{7}{%level} [%fiberId] %name:%line %message %cause}"

      val configProvider: ConfigProvider = ConfigProvider.fromMap(
        Map(
          "logger/format"                                              -> logFormat,
          "logger/path"                                                -> "file:///tmp/test.log",
          "logger/autoFlushBatchSize"                                  -> "2",
          "logger/bufferedIOSize"                                      -> "4096",
          "logger/rollingPolicy/type"                                  -> "TimeBasedRollingPolicy",
          "logger/filter/rootLevel"                                    -> LogLevel.Info.label,
          "logger/filter/mappings/zio.logging.example.LivePingService" -> LogLevel.Debug.label
        ),
        "/"
      )

      import zio.prelude._
      for {
        c1 <- configProvider.load(ConsoleLoggerConfig.config.nested("logger"))
        c2 <- configProvider.load(ConsoleLoggerConfig.config.nested("logger"))
      } yield assertTrue(
        c1.format == c2.format,
        c1 === c2
      )
    },
    test("fail on invalid charset and filter config") {
      val logFormat =
        "%highlight{%timestamp{yyyy-MM-dd'T'HH:mm:ssZ} %fixed{7}{%level} [%fiberId] %name:%line %message %cause}"

      val configProvider: ConfigProvider = ConfigProvider.fromMap(
        Map(
          "logger/format"           -> logFormat,
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
    test("fail on invalid format config") {
      val logFormat =
        "%highlight{%timestamp{yyyy-MM-dd'T'HH:mm:ssZ} %fixed{%level} [%fiberId] %name:%line %message %cause}"

      val configProvider: ConfigProvider = ConfigProvider.fromMap(
        Map(
          "logger/format" -> logFormat,
          "logger/path"   -> "file:///tmp/test.log"
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
