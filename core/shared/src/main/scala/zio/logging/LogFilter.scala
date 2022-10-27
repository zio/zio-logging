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

import zio.{ Cause, FiberId, FiberRefs, LogLevel, LogSpan, Trace, ZLogger }

import scala.annotation.tailrec

/**
 * A `LogFilter` represents function/conditions for log filtering
 */
trait LogFilter[-Message] { self =>

  type Group

  protected def group(
    trace: Trace,
    fiberId: FiberId,
    logLevel: LogLevel,
    message: () => Message,
    cause: Cause[Any],
    context: FiberRefs,
    spans: List[LogSpan],
    annotations: Map[String, String]
  ): Group

  protected def predicate(group: Group): Boolean

  final def apply(
    trace: Trace,
    fiberId: FiberId,
    logLevel: LogLevel,
    message: () => Message,
    cause: Cause[Any],
    context: FiberRefs,
    spans: List[LogSpan],
    annotations: Map[String, String]
  ): Boolean = {
    val g = group(trace, fiberId, logLevel, message, cause, context, spans, annotations)

    predicate(g)
  }

  /**
   * Returns a new log filter which satisfy result of this and given log filter
   */
  final def &&[M <: Message](other: LogFilter[M]): LogFilter[M] = and(other)

  /**
   * Returns a new log filter which satisfy result of this or given log filter
   */
  final def ||[M <: Message](other: LogFilter[M]): LogFilter[M] = or(other)

  /**
   * The alphanumeric version of the `&&` operator.
   */
  final def and[M <: Message](other: LogFilter[M]): LogFilter[M] =
    LogFilter[M, (self.Group, other.Group)](
      (
        trace: Trace,
        fiberId: FiberId,
        logLevel: LogLevel,
        message: () => M,
        cause: Cause[Any],
        context: FiberRefs,
        spans: List[LogSpan],
        annotations: Map[String, String]
      ) =>
        (
          self.group(trace, fiberId, logLevel, message, cause, context, spans, annotations),
          other.group(trace, fiberId, logLevel, message, cause, context, spans, annotations)
        ),
      g => {
        val (g1, g2) = g

        self.predicate(g1) && other.predicate(g2)
      }
    )

  /**
   * Returns a new log filter with cached results based on given log group
   */
  final def cached: LogFilter[Message] = {
    val cache = new java.util.concurrent.ConcurrentHashMap[Group, Boolean]()
    LogFilter[Message, self.Group](
      self.group,
      g =>
        cache.computeIfAbsent(
          g,
          _ => self.predicate(g)
        )
    )
  }

  final def contramap[M](f: M => Message): LogFilter[M] = LogFilter[M, self.Group](
    (
      trace: Trace,
      fiberId: FiberId,
      logLevel: LogLevel,
      message: () => M,
      cause: Cause[Any],
      context: FiberRefs,
      spans: List[LogSpan],
      annotations: Map[String, String]
    ) => self.group(trace, fiberId, logLevel, () => f(message()), cause, context, spans, annotations),
    self.predicate
  )

  /**
   * The alphanumeric version of the `!` operator.
   */
  final def not: LogFilter[Message] =
    LogFilter[Message, self.Group](self.group, g => !self.predicate(g))

  /**
   * The alphanumeric version of the `||` operator.
   */
  final def or[M <: Message](other: LogFilter[M]): LogFilter[M] =
    LogFilter[M, (self.Group, other.Group)](
      (
        trace: Trace,
        fiberId: FiberId,
        logLevel: LogLevel,
        message: () => M,
        cause: Cause[Any],
        context: FiberRefs,
        spans: List[LogSpan],
        annotations: Map[String, String]
      ) =>
        (
          self.group(trace, fiberId, logLevel, message, cause, context, spans, annotations),
          other.group(trace, fiberId, logLevel, message, cause, context, spans, annotations)
        ),
      g => {
        val (g1, g2) = g

        self.predicate(g1) || other.predicate(g2)
      }
    )

  /**
   * Returns a new log filter with negated result
   */
  final def unary_! : LogFilter[Message] = self.not

  /**
   * Returns a version of logger that only logs messages when this filter is satisfied
   */
  def filter[M <: Message, O](logger: zio.ZLogger[M, O]): zio.ZLogger[M, Option[O]] =
    new ZLogger[M, Option[O]] {
      override def apply(
        trace: Trace,
        fiberId: FiberId,
        logLevel: LogLevel,
        message: () => M,
        cause: Cause[Any],
        context: FiberRefs,
        spans: List[LogSpan],
        annotations: Map[String, String]
      ): Option[O] =
        if (self(trace, fiberId, logLevel, message, cause, context, spans, annotations)) {
          Some(logger(trace, fiberId, logLevel, message, cause, context, spans, annotations))
        } else None
    }
}

object LogFilter {

  def apply[M, G](
    group0: (Trace, FiberId, LogLevel, () => M, Cause[Any], FiberRefs, List[LogSpan], Map[String, String]) => G,
    predicate0: G => Boolean
  ): LogFilter[M] = new LogFilter[M] {
    override type Group = G

    override protected def group(
      trace: Trace,
      fiberId: FiberId,
      logLevel: LogLevel,
      message: () => M,
      cause: Cause[Any],
      context: FiberRefs,
      spans: List[LogSpan],
      annotations: Map[String, String]
    ): Group =
      group0(trace, fiberId, logLevel, message, cause, context, spans, annotations)

    override protected def predicate(value: Group): Boolean = predicate0(value)
  }

  /**
   * Log filter which accept all logs (logs are not filtered)
   */
  val acceptAll: LogFilter[Any] = apply[Any, Boolean](
    (
      _: Trace,
      _: FiberId,
      _: LogLevel,
      _: () => Any,
      _: Cause[Any],
      _: FiberRefs,
      _: List[LogSpan],
      _: Map[String, String]
    ) => true,
    identity
  )

  val causeNonEmpty: LogFilter[Any] = apply[Any, Cause[_]](
    (
      _: Trace,
      _: FiberId,
      _: LogLevel,
      _: () => Any,
      cause: Cause[Any],
      _: FiberRefs,
      _: List[LogSpan],
      _: Map[String, String]
    ) => cause,
    !_.isEmpty
  )

  /**
   * Returns a filter which accept logs when the log level satisfies the specified predicate
   */
  def logLevel(predicate: LogLevel => Boolean): LogFilter[Any] = apply[Any, LogLevel](
    (
      _: Trace,
      _: FiberId,
      logLevel: LogLevel,
      _: () => Any,
      _: Cause[Any],
      _: FiberRefs,
      _: List[LogSpan],
      _: Map[String, String]
    ) => logLevel,
    predicate
  )

  /**
   * Returns a filter which accept logs when the log level priority is higher then given one
   */
  def logLevel(rootLevel: LogLevel): LogFilter[Any] =
    logLevel(_ >= rootLevel)

  /**
   * Defines a filter for log filtering based log level specified by given groups,
   *
   * filter will use log level from first matching grouping or root level, if specific log level is not found
   *
   * @param rootLevel Default log level
   * @param group Log group
   * @param matcher Mather for log group and groupings
   * @param groupings Log levels definitions
   * @return A filter for log filtering based on given groups
   */
  def logLevelByGroup[M, A](
    rootLevel: LogLevel,
    group: LogGroup[M, A],
    matcher: (A, A) => Boolean,
    groupings: (A, LogLevel)*
  ): LogFilter[M] =
    apply[M, (A, LogLevel)](
      (trace, fiberId, level, message, cause, context, spans, annotations) => {
        val loggerGroup = group(trace, fiberId, level, message, cause, context, spans, annotations)
        loggerGroup -> level
      },
      g => {
        val (loggerGroup, level) = g
        val groupingLogLevel     = groupings.collectFirst {
          case (groupingGroup, groupingLevel) if matcher(loggerGroup, groupingGroup) => groupingLevel
        }.getOrElse(rootLevel)

        level >= groupingLogLevel
      }
    )

//  def logLevelByGroup[M, A](
//    rootLevel: LogLevel,
//    group: LogGroup[M, A],
//    equivalence: LogGroupRelation[A],
//    groupings: (A, LogLevel)*
//  ): LogFilter[M] =
//    (trace, fiberId, level, message, cause, context, spans, annotations) => {
//      val loggerGroup = group(trace, fiberId, level, message, cause, context, spans, annotations)
//
//      val groupingLogLevel = groupings.collectFirst {
//        case (groupingGroup, groupingLevel) if equivalence.related(loggerGroup, groupingGroup) => groupingLevel
//      }.getOrElse(rootLevel)
//
//      level >= groupingLogLevel
//    }
//
//  def logLevelByGroupEq[M, A](
//    rootLevel: LogLevel,
//    group: LogGroup[M, A],
//    groupings: (A, LogLevel)*
//  ): LogFilter[M] =
//    (trace, fiberId, level, message, cause, context, spans, annotations) => {
//      val loggerGroupEq = group.related(trace, fiberId, level, message, cause, context, spans, annotations) _
//
//      val groupingLogLevel = groupings.collectFirst {
//        case (groupingGroup, groupingLevel) if loggerGroupEq(groupingGroup) => groupingLevel
//      }.getOrElse(rootLevel)
//
//      level >= groupingLogLevel
//    }

  /**
   * Defines a filter from a list of log-levels specified per tree node
   *
   * Example:
   *
   * {{{
   *   val filter =
   *     logLevelByName(
   *      LogLevel.Debug,
   *      "io.netty"                                       -> LogLevel.Info,
   *      "io.grpc.netty"                                  -> LogLevel.Info
   * )
   * }}}
   *
   * will use the `Debug` log level for everything except for log events with the logger name
   * prefixed by either `List("io", "netty")` or `List("io", "grpc", "netty")`.
   * Logger name is extracted from [[Trace]].
   *
   * @param rootLevel Minimum log level for the root node
   * @param mappings  List of mappings, nesting defined by dot-separated strings
   * @return A filter for log filtering based on log level and name
   */
  def logLevelByName[M](rootLevel: LogLevel, mappings: (String, LogLevel)*): LogFilter[M] =
    logLevelByGroup[M](rootLevel, LogGroup.loggerName, mappings: _*)

  /**
   * Defines a filter from a list of log-levels specified per tree node
   *
   * Example:
   *
   * {{{
   *   val filter =
   *     logLevelByGroup(
   *      LogLevel.Debug,
   *      "io.netty"                                       -> LogLevel.Info,
   *      "io.grpc.netty"                                  -> LogLevel.Info
   * )
   * }}}
   *
   * will use the `Debug` log level for everything except for log events with the logger name
   * prefixed by either `List("io", "netty")` or `List("io", "grpc", "netty")`.
   *
   * @param rootLevel  Minimum log level for the root node
   * @param group Log group
   * @param mappings   List of mappings, nesting defined by dot-separated strings
   * @return A filter for log filtering based on log level and name
   */
  def logLevelByGroup[M](
    rootLevel: LogLevel,
    group: LogGroup[M, String],
    mappings: (String, LogLevel)*
  ): LogFilter[M] = {

    /**
     * mappings:
     * a.b.PingPong -> Debug,
     * a.b.Ping -> Info,
     * a.b -> Error,
     *
     *  List(a, b, Ping) -> a.b.Ping
     *
     * loggers with names
     * a.b.Ping -> Info
     * a.b.PingPong -> Debug
     * a.b.c.Pong -> Error
     * a.c.Foo -> rootLevel
     */
    val mappingsSorted = mappings.map(splitNameByDotAndLevel.tupled).sorted(nameLevelOrdering)
    val nameGroup      = group.transform(splitNameByDot)(_.mkString("."))

    logLevelByGroup[M, List[String]](
      rootLevel,
      nameGroup,
      (l, m) => l.startsWith(m),
      mappingsSorted: _*
    )
  }

  private[logging] val splitNameByDotAndLevel: (String, LogLevel) => (List[String], LogLevel) = (name, level) =>
    splitNameByDot(name) -> level

  /**
   * split name by '.'
   *
   * example: `io.grpc.netty` -> `List("io", "grpc", "netty")`
   */
  private[logging] val splitNameByDot: String => List[String] = _.split('.').toList

  /**
   * ordering by name and level, where most specific names are at first places
   *
   * for example, input:
   * {{{
   *  Seq(
   *    "a"              -> LogLevel.Warning,
   *    "a"              -> LogLevel.Info,
   *    "a.b.c.Service1" -> LogLevel.Warning,
   *    "a.b.c"          -> LogLevel.Error,
   *    "a.b.d"          -> LogLevel.Debug,
   *    "e.f"            -> LogLevel.Error
   *  )
   * }}}
   *
   * will be sorted as:
   *
   * {{{
   *  Seq(
   *    "e.f"            -> LogLevel.Error,
   *    "a.b.d"          -> LogLevel.Debug,
   *    "a.b.c.Service1" -> LogLevel.Warning,
   *    "a.b.c"          -> LogLevel.Error,
   *    "a"              -> LogLevel.Info,
   *    "a"              -> LogLevel.Warning
   *  )
   * }}}
   */
  private[logging] val nameLevelOrdering: Ordering[(List[String], LogLevel)] = new Ordering[(List[String], LogLevel)] {

    @tailrec
    def compareNames(x: List[String], y: List[String]): Int =
      (x, y) match {
        case (_ :: _, Nil)                      => -1
        case (Nil, _ :: _)                      => 1
        case (xFirst :: xTail, yFirst :: yTail) =>
          val r = yFirst.compareTo(xFirst)
          if (r != 0) {
            r
          } else compareNames(xTail, yTail)

        case _ => 0
      }

    override def compare(x: (List[String], LogLevel), y: (List[String], LogLevel)): Int = {
      val (xName, xLevel) = x
      val (yName, yLevel) = y
      val r               = compareNames(xName, yName)
      if (r == 0) { // paths are same
        xLevel.ordinal - yLevel.ordinal
      } else r
    }
  }
}
