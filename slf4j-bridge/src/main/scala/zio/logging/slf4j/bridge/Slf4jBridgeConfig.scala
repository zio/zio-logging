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

import zio.{ Config, NonEmptyChunk, ZIO, ZLayer }

/**
 * SLF4J bridge configuration
 */
case class Slf4jBridgeConfig(fiberRefPropagation: Boolean, loggerNameLogSpan: Boolean)

object Slf4jBridgeConfig {
  val default: Slf4jBridgeConfig = Slf4jBridgeConfig(fiberRefPropagation = true, loggerNameLogSpan = true)

  val config: Config[Slf4jBridgeConfig] = {

    val fiberRefPropagationConfig = Config.boolean("fiberRefPropagation").withDefault(true)
    val loggerNameLogSpanConfig   = Config.boolean("loggerNameLogSpan").withDefault(true)

    (fiberRefPropagationConfig ++ loggerNameLogSpanConfig).map { case (fiberRefPropagation, loggerNameLogSpan) =>
      Slf4jBridgeConfig(fiberRefPropagation, loggerNameLogSpan)
    }
  }

  def load(
    configPath: NonEmptyChunk[String] = Slf4jBridge.bridgeConfigPath
  ): ZIO[Any, Config.Error, Slf4jBridgeConfig] =
    ZIO.config(Slf4jBridgeConfig.config.nested(configPath.head, configPath.tail: _*))

  def make(
    configPath: NonEmptyChunk[String] = Slf4jBridge.bridgeConfigPath
  ): ZLayer[Any, Config.Error, Slf4jBridgeConfig] =
    ZLayer.fromZIO(load(configPath))
}
