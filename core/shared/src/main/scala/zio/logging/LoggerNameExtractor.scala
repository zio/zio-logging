/*
 * Copyright 2019-2022 John A. De Goes and the ZIO Contributors
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
package zio.logging

import zio.{ FiberRefs, Trace }

trait LoggerNameExtractor {

  def apply(
    trace: Trace,
    context: FiberRefs,
    annotations: Map[String, String]
  ): String

}

object LoggerNameExtractor {

  /**
   * get logger name from [[Trace]]
   *
   * trace with value ''example.LivePingService.ping(PingService.scala:22)''
   * will have ''example.LivePingService'' as logger name
   */
  val trace: LoggerNameExtractor = trace("zio-logger")

  /**
   * get logger name from [[Trace]]
   *
   * trace with value ''example.LivePingService.ping(PingService.scala:22)''
   * will have ''example.LivePingService'' as logger name
   */
  def trace(default: String): LoggerNameExtractor = (trace, _, _) =>
    trace match {
      case Trace(location, _, _) =>
        val last = location.lastIndexOf(".")
        if (last > 0) {
          location.substring(0, last)
        } else location
      case _                     => default
    }

  def annotation(name: String, default: String = "zio-logger"): LoggerNameExtractor = (_, _, annotations) =>
    annotations.getOrElse(name, default)

  def annotationOrTrace(name: String, default: String = "zio-logger"): LoggerNameExtractor =
    (t, context, annotations) => annotations.getOrElse(name, trace(default)(t, context, annotations))

}
