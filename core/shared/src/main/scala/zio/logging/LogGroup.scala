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

import zio.{ FiberRefs, LogLevel, Trace, Unzippable, Zippable }

trait LogGroup[A] { self =>

  def apply(
    trace: Trace,
    logLevel: LogLevel,
    context: FiberRefs,
    annotations: Map[String, String]
  ): A

  def relation: LogGroupEquivalence[A] = LogGroupEquivalence.default

  def equivalent(
    trace: Trace,
    logLevel: LogLevel,
    context: FiberRefs,
    annotations: Map[String, String]
  )(value: A): Boolean =
    relation.equivalent(self(trace, logLevel, context, annotations), value)

  /**
   * Combine this log group with specified log group
   */
  final def ++[B, O](
    other: LogGroup[B]
  )(implicit zippable: Zippable.Out[A, B, O], unzippable: Unzippable.In[A, B, O]): LogGroup[O] = zip(other)

  /**
   * Returns new log group whose result is mapped by the specified f function.
   */
  final def map[B](f: A => B): LogGroup[B] = new LogGroup[B] {
    override def apply(trace: Trace, logLevel: LogLevel, context: FiberRefs, annotations: Map[String, String]): B =
      f(self(trace, logLevel, context, annotations))
  }

  final def map[B](f: A => B, r: LogGroupEquivalence[B]): LogGroup[B] = new LogGroup[B] {
    override def relation: LogGroupEquivalence[B]                                                                 = r
    override def apply(trace: Trace, logLevel: LogLevel, context: FiberRefs, annotations: Map[String, String]): B = f(
      self(trace, logLevel, context, annotations)
    )
  }

  /**
   * Combine this log group with specified log group
   */
  final def zip[B, O](
    other: LogGroup[B]
  )(implicit zippable: Zippable.Out[A, B, O], unzippable: Unzippable.In[A, B, O]): LogGroup[O] = new LogGroup[O] {
    override def relation: LogGroupEquivalence[O] =
      LogGroupEquivalence { (l, r) =>
        val (sl, ol) = unzippable.unzip(l)
        val (sr, or) = unzippable.unzip(r)
        self.relation.equivalent(sl, sr) && other.relation.equivalent(ol, or)
      }

    override def apply(
      trace: Trace,
      logLevel: LogLevel,
      context: FiberRefs,
      annotations: Map[String, String]
    ): O =
      zippable.zip(self(trace, logLevel, context, annotations), other(trace, logLevel, context, annotations))
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

}

object LogGroup {

  def apply[A](
    group: (Trace, LogLevel, FiberRefs, Map[String, String]) => A,
    equivalence: LogGroupEquivalence[A]
  ): LogGroup[A] = new LogGroup[A] {
    override def relation: LogGroupEquivalence[A]                                                                 = equivalence
    override def apply(trace: Trace, logLevel: LogLevel, context: FiberRefs, annotations: Map[String, String]): A =
      group(trace, logLevel, context, annotations)
  }

  def fromLoggerNameExtractor(
    loggerNameExtractor: LoggerNameExtractor,
    loggerNameDefault: String = "zio-logger"
  ): LogGroup[String] =
    apply(
      (trace, _, context, annotations) => loggerNameExtractor(trace, context, annotations).getOrElse(loggerNameDefault),
      LogGroupEquivalence.stringStartWith
    )

  /**
   * Log group by level
   */
  val logLevel: LogGroup[LogLevel] = (_, logLevel, _, _) => logLevel

  /**
   * Log group by logger name
   *
   * Logger name is extracted from [[Trace]]
   */
  val loggerName: LogGroup[String] = fromLoggerNameExtractor(LoggerNameExtractor.trace)

  /**
   * Log group by logger name and log level
   *
   * Logger name is extracted from [[Trace]]
   */
  val loggerNameAndLevel: LogGroup[(String, LogLevel)] = loggerName ++ logLevel
}
