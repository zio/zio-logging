/*
 * Copyright 2019-2026 John A. De Goes and the ZIO Contributors
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

/**
 * The name of an SLF4J logger that the bridge created.
 */
final case class LoggerData(name: String) {

  /**
   * The log annotations that the bridge adds to every line of this logger: `logger_name` set to `name`.
   */
  lazy val annotations: Map[String, String] = Map(zio.logging.loggerNameAnnotationKey -> name)

}
