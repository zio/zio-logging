/*
 * Copyright 2019-2023 John A. De Goes and the ZIO Contributors
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

import zio.{ Cause, FiberId, FiberRefs, LogLevel, LogSpan, Trace, Zippable }

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

  /**
   * Combine this log group with specified log group
   */
  final def ++[M <: Message, O, Out2](
    other: LogGroup[M, O]
  )(implicit zippable: Zippable.Out[Out, O, Out2]): LogGroup[M, Out2] = zip(
    other
  )

  final def contramap[M](f: M => Message): LogGroup[M, Out] = new LogGroup[M, Out] {
    override def apply(
      trace: Trace,
      fiberId: FiberId,
      logLevel: LogLevel,
      message: () => M,
      cause: Cause[Any],
      context: FiberRefs,
      spans: List[LogSpan],
      annotations: Map[String, String]
    ): Out =
      self(trace, fiberId, logLevel, () => f(message()), cause, context, spans, annotations)
  }

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

  /**
   * Combine this log group with specified log group
   */
  final def zip[M <: Message, O, Out2](
    other: LogGroup[M, O]
  )(implicit zippable: Zippable.Out[Out, O, Out2]): LogGroup[M, Out2] =
    new LogGroup[M, Out2] {
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
    f: (Trace, FiberId, LogLevel, () => M, Cause[Any], FiberRefs, List[LogSpan], Map[String, String]) => O
  ): LogGroup[M, O] = new LogGroup[M, O] {
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
      f(trace, fiberId, logLevel, message, cause, context, spans, annotations)
  }

  /**
   * Log group by cause
   */
  val cause: LogGroup[Any, Cause[Any]] = apply((_, _, _, _, cause, _, _, _) => cause)

  /**
   * Log group with given constant value
   */
  def constant[O](value: O): LogGroup[Any, O] = apply((_, _, _, _, _, _, _, _) => value)

  def fromLoggerNameExtractor(
    loggerNameExtractor: LoggerNameExtractor,
    loggerNameDefault: String = "zio-logger"
  ): LogGroup[Any, String] =
    apply((trace, _, _, _, _, context, _, annotations) =>
      loggerNameExtractor(trace, context, annotations).getOrElse(loggerNameDefault)
    )

  /**
   * Log group by level
   */
  val logLevel: LogGroup[Any, LogLevel] = apply((_, _, logLevel, _, _, _, _, _) => logLevel)

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
