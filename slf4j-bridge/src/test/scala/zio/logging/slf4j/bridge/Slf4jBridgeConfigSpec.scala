/*
 * Copyright 2019-2025 John A. De Goes and the ZIO Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package zio.logging.slf4j.bridge

import zio.test._
import zio.{ Config, ConfigProvider }

object Slf4jBridgeConfigSpec extends ZIOSpecDefault {
  private val configPath = Slf4jBridge.bridgeConfigPath

  private def testConfig(
    name: String,
    configMap: Map[String, String],
    expected: Slf4jBridgeConfig
  ) =
    test(name) {
      val configProvider = ConfigProvider.fromMap(configMap, "/")

      Slf4jBridgeConfig
        .load(configPath)
        .provide(zio.Runtime.setConfigProvider(configProvider))
        .map(actual => assertTrue(actual == expected))
    }

  private def testErrorConfig(
    name: String,
    configMap: Map[String, String],
    assertion: Assertion[Config.Error]
  ) =
    test(name) {
      val configProvider = ConfigProvider.fromMap(configMap, "/")

      Slf4jBridgeConfig
        .load(configPath)
        .provide(zio.Runtime.setConfigProvider(configProvider))
        .exit
        .map(exit => assert(exit)(Assertion.fails(assertion)))
    }

  val spec: Spec[Environment, Any] = suite("Slf4jBridgeConfig") {
    suite("config loading")(
      testConfig(
        "load valid config with all values",
        Map(
          "logger/bridge/fiberRefPropagation" -> "false",
          "logger/bridge/loggerNameLogSpan"   -> "false"
        ),
        Slf4jBridgeConfig(fiberRefPropagation = false, loggerNameLogSpan = false)
      ),
      testConfig(
        "load default config with missing values",
        Map.empty,
        Slf4jBridgeConfig.default
      ),
      testConfig(
        "load partial config with some values",
        Map("logger/bridge/loggerNameLogSpan" -> "false"),
        Slf4jBridgeConfig.default.copy(loggerNameLogSpan = false)
      ),
      test("equals config with same sources") {
        val configProvider = ConfigProvider.fromMap(
          Map(
            "logger/bridge/fiberRefPropagation" -> "false",
            "logger/bridge/loggerNameLogSpan"   -> "true"
          ),
          "/"
        )

        (for {
          c1 <- Slf4jBridgeConfig.load(configPath)
          c2 <- Slf4jBridgeConfig.load(configPath)
        } yield assertTrue(c1 == c2)).provide(zio.Runtime.setConfigProvider(configProvider))
      },
      testErrorConfig(
        "fail on invalid boolean value",
        Map("logger/bridge/fiberRefPropagation" -> "not-a-boolean"),
        Assertion.anything
      )
    )

  }
}
