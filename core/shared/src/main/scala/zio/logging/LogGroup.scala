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

import zio.{ FiberRefs, LogLevel, Trace, Zippable }

trait LogGroup[A] { self =>

  def apply(
    trace: Trace,
    logLevel: LogLevel,
    context: FiberRefs,
    annotations: Map[String, String]
  ): A

  /**
   * Returns new log group whose result is mapped by the specified f function.
   */
  final def map[B](f: A => B): LogGroup[B] = new LogGroup[B] {
    override def apply(trace: Trace, logLevel: LogLevel, context: FiberRefs, annotations: Map[String, String]): B =
      f(self(trace, logLevel, context, annotations))
  }

  /**
   * Zips this log group together with the specified log group using the combination functions.
   */
  final def zipWith[B, C](
    other: LogGroup[B]
  )(f: (A, B) => C): LogGroup[C] = new LogGroup[C] {
    override def apply(
      trace: Trace,
      logLevel: LogLevel,
      context: FiberRefs,
      annotations: Map[String, String]
    ): C =
      f(self(trace, logLevel, context, annotations), other(trace, logLevel, context, annotations))
  }

  /**
   * Combine this log group with specified log group
   */
  final def ++[B](
    other: LogGroup[B]
  )(implicit zippable: Zippable[A, B]): LogGroup[zippable.Out] = new LogGroup[zippable.Out] {
    override def apply(
      trace: Trace,
      logLevel: LogLevel,
      context: FiberRefs,
      annotations: Map[String, String]
    ): zippable.Out =
      zippable.zip(self(trace, logLevel, context, annotations), other(trace, logLevel, context, annotations))
  }

}

object LogGroup {

  def make(loggerNameExtractor: LoggerNameExtractor): LogGroup[String] =
    (trace, _, context, annotations) => loggerNameExtractor(trace, context, annotations)

  /**
   * Log group by level
   */
  val level: LogGroup[LogLevel] = (_, logLevel, _, _) => logLevel

  /**
   * Log group by logger name
   *
   * Logger name is extracted from [[Trace]]
   */
  val loggerName: LogGroup[String] = make(LoggerNameExtractor.trace)

  /**
   * Log group by logger name and log level
   *
   * Logger name is extracted from [[Trace]]
   */
  val loggerNameAndLevel: LogGroup[(String, LogLevel)] = loggerName ++ level
}
