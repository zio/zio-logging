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

import zio.{ Cause, FiberId, FiberRefs, LogLevel, LogSpan, Trace, Unzippable, Zippable }

trait LogGroup[-Message, Out] { self =>

  def apply(
    trace: Trace,
    fiberId: FiberId,
    logLevel: LogLevel,
    message: () => Message,
    cause: Cause[Any],
    context: FiberRefs,
    spans: List[LogSpan],
    annotations: Map[String, String]
  ): Out

  def relation: LogGroupEquivalence[Out] = LogGroupEquivalence.default

  def equivalent(
    trace: Trace,
    fiberId: FiberId,
    logLevel: LogLevel,
    message: () => Message,
    cause: Cause[Any],
    context: FiberRefs,
    spans: List[LogSpan],
    annotations: Map[String, String]
  )(value: Out): Boolean =
    relation.equivalent(self(trace, fiberId, logLevel, message, cause, context, spans, annotations), value)

  /**
   * Combine this log group with specified log group
   */
  final def ++[M <: Message, O, Out2](
    other: LogGroup[M, O]
  )(implicit zippable: Zippable.Out[Out, O, Out2], unzippable: Unzippable.In[Out, O, Out2]): LogGroup[M, Out2] = zip(
    other
  )

  /**
   * Returns new log group whose result is mapped by the specified f function.
   */
  final def map[O](f: Out => O): LogGroup[Message, O] = new LogGroup[Message, O] {
    override def apply(
      trace: Trace,
      fiberId: FiberId,
      logLevel: LogLevel,
      message: () => Message,
      cause: Cause[Any],
      context: FiberRefs,
      spans: List[LogSpan],
      annotations: Map[String, String]
    ): O =
      f(self(trace, fiberId, logLevel, message, cause, context, spans, annotations))
  }

  final def map[O](f: Out => O, r: LogGroupEquivalence[O]): LogGroup[Message, O] = new LogGroup[Message, O] {
    override def relation: LogGroupEquivalence[O] = r
    override def apply(
      trace: Trace,
      fiberId: FiberId,
      logLevel: LogLevel,
      message: () => Message,
      cause: Cause[Any],
      context: FiberRefs,
      spans: List[LogSpan],
      annotations: Map[String, String]
    ): O = f(
      self(trace, fiberId, logLevel, message, cause, context, spans, annotations)
    )
  }

  /**
   * Combine this log group with specified log group
   */
  final def zip[M <: Message, O, Out2](
    other: LogGroup[M, O]
  )(implicit zippable: Zippable.Out[Out, O, Out2], unzippable: Unzippable.In[Out, O, Out2]): LogGroup[M, Out2] =
    new LogGroup[M, Out2] {
      override def relation: LogGroupEquivalence[Out2] =
        LogGroupEquivalence { (l, r) =>
          val (sl, ol) = unzippable.unzip(l)
          val (sr, or) = unzippable.unzip(r)
          self.relation.equivalent(sl, sr) && other.relation.equivalent(ol, or)
        }

      override def apply(
        trace: Trace,
        fiberId: FiberId,
        logLevel: LogLevel,
        message: () => M,
        cause: Cause[Any],
        context: FiberRefs,
        spans: List[LogSpan],
        annotations: Map[String, String]
      ): Out2 =
        zippable.zip(
          self(trace, fiberId, logLevel, message, cause, context, spans, annotations),
          other(trace, fiberId, logLevel, message, cause, context, spans, annotations)
        )
    }

  /**
   * Zips this log group together with the specified log group using the combination functions.
   */
  final def zipWith[M <: Message, O, Out2](
    other: LogGroup[M, O]
  )(f: (Out, O) => Out2): LogGroup[M, Out2] = new LogGroup[M, Out2] {
    override def apply(
      trace: Trace,
      fiberId: FiberId,
      logLevel: LogLevel,
      message: () => M,
      cause: Cause[Any],
      context: FiberRefs,
      spans: List[LogSpan],
      annotations: Map[String, String]
    ): Out2 =
      f(
        self(trace, fiberId, logLevel, message, cause, context, spans, annotations),
        other(trace, fiberId, logLevel, message, cause, context, spans, annotations)
      )
  }

}

object LogGroup {

  def apply[M, O](
    group: (Trace, FiberId, LogLevel, () => M, Cause[Any], FiberRefs, List[LogSpan], Map[String, String]) => O,
    equivalence: LogGroupEquivalence[O]
  ): LogGroup[M, O] = new LogGroup[M, O] {
    override def relation: LogGroupEquivalence[O] = equivalence
    override def apply(
      trace: Trace,
      fiberId: FiberId,
      logLevel: LogLevel,
      message: () => M,
      cause: Cause[Any],
      context: FiberRefs,
      spans: List[LogSpan],
      annotations: Map[String, String]
    ): O =
      group(trace, fiberId, logLevel, message, cause, context, spans, annotations)
  }

  def fromLoggerNameExtractor(
    loggerNameExtractor: LoggerNameExtractor,
    loggerNameDefault: String = "zio-logger"
  ): LogGroup[Any, String] =
    apply(
      (trace, _, _, _, _, context, _, annotations) =>
        loggerNameExtractor(trace, context, annotations).getOrElse(loggerNameDefault),
      LogGroupEquivalence.stringStartWith
    )

  /**
   * Log group by level
   */
  val logLevel: LogGroup[Any, LogLevel] = (_, _, logLevel, _, _, _, _, _) => logLevel

  /**
   * Log group by logger name
   *
   * Logger name is extracted from [[Trace]]
   */
  val loggerName: LogGroup[Any, String] = fromLoggerNameExtractor(LoggerNameExtractor.trace)

  /**
   * Log group by logger name and log level
   *
   * Logger name is extracted from [[Trace]]
   */
  val loggerNameAndLevel: LogGroup[Any, (String, LogLevel)] = loggerName ++ logLevel
}
