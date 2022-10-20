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

trait LoggerNameExtractor { self =>

  def apply(
    trace: Trace,
    context: FiberRefs,
    annotations: Map[String, String]
  ): Option[String]

  /**
   * Returns a new extractor which return logger name from this one or other if this is empty
   */
  final def ||(other: LoggerNameExtractor): LoggerNameExtractor =
    self.or(other)

  /**
   * The alphanumeric version of the `||` operator.
   */
  final def or(other: LoggerNameExtractor): LoggerNameExtractor =
    (trace, context, annotations) => self(trace, context, annotations).orElse(other(trace, context, annotations))

  /**
   * Converts this extractor into a log format
   */
  final def toLogFormat(default: String = "zio-logger"): LogFormat = LogFormat.loggerName(self, default)

  /**
   * Converts this extractor into a log group
   */
  final def toLogGroup(default: String = "zio-logger"): LogGroup[String] =
    LogGroup.fromLoggerNameExtractor(self, default)

}

object LoggerNameExtractor {

  /**
   * Extractor which take logger name from annotation
   *
   * @param name name of annotation
   */
  def annotation(name: String): LoggerNameExtractor = (_, _, annotations) => annotations.get(name)

  /**
   * Extractor which take logger name from annotation or [[Trace]] if specified annotation is not present
   *
   * @param name name of annotation
   */
  def annotationOrTrace(name: String): LoggerNameExtractor =
    annotation(name) || trace

  /**
   * Extractor which take logger name from [[Trace]]
   *
   * trace with value ''example.LivePingService.ping(PingService.scala:22)''
   * will have ''example.LivePingService'' as logger name
   */
  val trace: LoggerNameExtractor = (trace, _, _) =>
    trace match {
      case Trace(location, _, _) =>
        val last = location.lastIndexOf(".")
        val name = if (last > 0) {
          location.substring(0, last)
        } else location
        Some(name)
      case _                     => None
    }

}
