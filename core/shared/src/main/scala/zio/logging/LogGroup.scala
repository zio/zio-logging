/*
 * Copyright 2019-2024 John A. De Goes and the ZIO Contributors
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

sealed trait LogGroup[-Message, Out] { self =>

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

  final def contramap[M](f: M => Message): LogGroup[M, Out] = LogGroup.ContramapGroup(self, f)

  /**
   * Returns new log group whose result is mapped by the specified f function.
   */
  final def map[O](f: Out => O): LogGroup[Message, O] = LogGroup.MapGroup(self, f)

  /**
   * Combine this log group with specified log group
   */
  final def zip[M <: Message, O, Out2](
    other: LogGroup[M, O]
  )(implicit zippable: Zippable.Out[Out, O, Out2]): LogGroup[M, Out2] = LogGroup.ZipGroup(self, other)

  /**
   * Zips this log group together with the specified log group using the combination functions.
   */
  final def zipWith[M <: Message, O, Out2](
    other: LogGroup[M, O]
  )(f: (Out, O) => Out2): LogGroup[M, Out2] = LogGroup.ZipWithGroup(self, other, f)

}

object LogGroup {

  private[logging] final case class FnGroup[-Message, Out](
    fn: (
      Trace,
      FiberId,
      LogLevel,
      () => Message,
      Cause[Any],
      FiberRefs,
      List[LogSpan],
      Map[String, String]
    ) => Out
  ) extends LogGroup[Message, Out] {
    override def apply(
      trace: Trace,
      fiberId: FiberId,
      logLevel: LogLevel,
      message: () => Message,
      cause: Cause[Any],
      context: FiberRefs,
      spans: List[LogSpan],
      annotations: Map[String, String]
    ): Out =
      fn(trace, fiberId, logLevel, message, cause, context, spans, annotations)
  }

  private[logging] final case class LoggerNameExtractorGroup(
    loggerNameExtractor: LoggerNameExtractor,
    loggerNameDefault: String
  ) extends LogGroup[Any, String] {
    override def apply(
      trace: Trace,
      fiberId: FiberId,
      logLevel: LogLevel,
      message: () => Any,
      cause: Cause[Any],
      context: FiberRefs,
      spans: List[LogSpan],
      annotations: Map[String, String]
    ): String =
      loggerNameExtractor(trace, context, annotations).getOrElse(loggerNameDefault)
  }

  private[logging] final case class ConstantGroup[Output](
    constant: Output
  ) extends LogGroup[Any, Output] {
    override def apply(
      trace: Trace,
      fiberId: FiberId,
      logLevel: LogLevel,
      message: () => Any,
      cause: Cause[Any],
      context: FiberRefs,
      spans: List[LogSpan],
      annotations: Map[String, String]
    ): Output =
      constant
  }

  private[logging] final case class ZipGroup[Message, Out1, Out2, Out](
    first: LogGroup[Message, Out1],
    second: LogGroup[Message, Out2]
  )(implicit zippable: Zippable.Out[Out1, Out2, Out])
      extends LogGroup[Message, Out] {

    override def apply(
      trace: Trace,
      fiberId: FiberId,
      logLevel: LogLevel,
      message: () => Message,
      cause: Cause[Any],
      context: FiberRefs,
      spans: List[LogSpan],
      annotations: Map[String, String]
    ): Out =
      zippable.zip(
        first(trace, fiberId, logLevel, message, cause, context, spans, annotations),
        second(trace, fiberId, logLevel, message, cause, context, spans, annotations)
      )
  }

  private[logging] final case class ZipWithGroup[Message, Out1, Out2, Out](
    first: LogGroup[Message, Out1],
    second: LogGroup[Message, Out2],
    fn: (Out1, Out2) => Out
  ) extends LogGroup[Message, Out] {

    override def apply(
      trace: Trace,
      fiberId: FiberId,
      logLevel: LogLevel,
      message: () => Message,
      cause: Cause[Any],
      context: FiberRefs,
      spans: List[LogSpan],
      annotations: Map[String, String]
    ): Out =
      fn(
        first(trace, fiberId, logLevel, message, cause, context, spans, annotations),
        second(trace, fiberId, logLevel, message, cause, context, spans, annotations)
      )
  }

  private[logging] final case class MapGroup[Message, Out1, Out2](
    group: LogGroup[Message, Out1],
    fn: Out1 => Out2
  ) extends LogGroup[Message, Out2] {

    override def apply(
      trace: Trace,
      fiberId: FiberId,
      logLevel: LogLevel,
      message: () => Message,
      cause: Cause[Any],
      context: FiberRefs,
      spans: List[LogSpan],
      annotations: Map[String, String]
    ): Out2 =
      fn(
        group(trace, fiberId, logLevel, message, cause, context, spans, annotations)
      )
  }

  private[logging] final case class ContramapGroup[Message1, Message2, Out](
    group: LogGroup[Message1, Out],
    fn: Message2 => Message1
  ) extends LogGroup[Message2, Out] {

    override def apply(
      trace: Trace,
      fiberId: FiberId,
      logLevel: LogLevel,
      message: () => Message2,
      cause: Cause[Any],
      context: FiberRefs,
      spans: List[LogSpan],
      annotations: Map[String, String]
    ): Out =
      group(trace, fiberId, logLevel, () => fn(message()), cause, context, spans, annotations)

  }

  def apply[M, O](
    fn: (Trace, FiberId, LogLevel, () => M, Cause[Any], FiberRefs, List[LogSpan], Map[String, String]) => O
  ): LogGroup[M, O] = FnGroup(fn)

  /**
   * Log group by cause
   */
  val cause: LogGroup[Any, Cause[Any]] = apply((_, _, _, _, cause, _, _, _) => cause)

  /**
   * Log group with given constant value
   */
  def constant[O](value: O): LogGroup[Any, O] = ConstantGroup(value)

  def fromLoggerNameExtractor(
    loggerNameExtractor: LoggerNameExtractor,
    loggerNameDefault: String = "zio-logger"
  ): LogGroup[Any, String] = LoggerNameExtractorGroup(loggerNameExtractor, loggerNameDefault)

  /**
   * Log group by level
   */
  val logLevel: LogGroup[Any, LogLevel] = apply((_, _, logLevel, _, _, _, _, _) => logLevel)

  /**
   * Log group by logger name
   *
   * Logger name is extracted from annotation or [[Trace]], see: [[LoggerNameExtractor.loggerNameAnnotationOrTrace]]
   */
  val loggerName: LogGroup[Any, String] = LoggerNameExtractor.loggerNameAnnotationOrTrace.toLogGroup()

  /**
   * Log group by logger name and log level
   *
   * Logger name is extracted from annotation or [[Trace]]
   */
  val loggerNameAndLevel: LogGroup[Any, (String, LogLevel)] = loggerName ++ logLevel

}
