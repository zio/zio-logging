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

  def apply(
    trace: Trace,
    fiberId: FiberId,
    logLevel: LogLevel,
    message: () => Message,
    cause: Cause[Any],
    context: FiberRefs,
    spans: List[LogSpan],
    annotations: Map[String, String]
  ): Boolean

  final def contramap[M](f: M => Message): LogFilter[M] = (
    trace: Trace,
    fiberId: FiberId,
    logLevel: LogLevel,
    message: () => M,
    cause: Cause[Any],
    context: FiberRefs,
    spans: List[LogSpan],
    annotations: Map[String, String]
  ) => self(trace, fiberId, logLevel, () => f(message()), cause, context, spans, annotations)

  /**
   * The alphanumeric version of the `&&` operator.
   */
  final def and[M <: Message](other: LogFilter[M]): LogFilter[M] =
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
      self(trace, fiberId, logLevel, message, cause, context, spans, annotations) && other(
        trace,
        fiberId,
        logLevel,
        message,
        cause,
        context,
        spans,
        annotations
      )

  /**
   * Returns a new log filter which satisfy result of this and given log filter
   */
  final def &&[M <: Message](other: LogFilter[M]): LogFilter[M] = and(other)

  /**
   * The alphanumeric version of the `||` operator.
   */
  final def or[M <: Message](other: LogFilter[M]): LogFilter[M] =
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
      self(trace, fiberId, logLevel, message, cause, context, spans, annotations) || other(
        trace,
        fiberId,
        logLevel,
        message,
        cause,
        context,
        spans,
        annotations
      )

  /**
   * Returns a new log filter which satisfy result of this or given log filter
   */
  final def ||[M <: Message](other: LogFilter[M]): LogFilter[M] = or(other)

  /**
   * The alphanumeric version of the `!` operator.
   */
  final def not: LogFilter[Message] = (
    trace: Trace,
    fiberId: FiberId,
    logLevel: LogLevel,
    message: () => Message,
    cause: Cause[Any],
    context: FiberRefs,
    spans: List[LogSpan],
    annotations: Map[String, String]
  ) => !self(trace, fiberId, logLevel, message, cause, context, spans, annotations)

  /**
   * Returns a new log filter with negated result
   */
  final def unary_! : LogFilter[Message] = self.not

  /**
   * Returns a new log filter with cached results based on given log group
   */
  final def cacheWith[A](group: LogGroup[A]): LogFilter[Message] =
    new LogFilter[Message] {
      private val cache = new java.util.concurrent.ConcurrentHashMap[A, Boolean]()

      override def apply(
        trace: Trace,
        fiberId: FiberId,
        logLevel: LogLevel,
        message: () => Message,
        cause: Cause[Any],
        context: FiberRefs,
        spans: List[LogSpan],
        annotations: Map[String, String]
      ): Boolean = {
        val key = group(trace, logLevel, context, annotations)
        cache.computeIfAbsent(
          key,
          _ => self(trace, fiberId, logLevel, message, cause, context, spans, annotations)
        )
      }
    }

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

  private final case class LevelNode(logLevel: LogLevel, children: Map[String, LevelNode])

  /**
   * Log filter which accept all logs (logs are not filtered)
   */
  val acceptAll: LogFilter[Any] =
    (
      _: Trace,
      _: FiberId,
      _: LogLevel,
      _: () => Any,
      _: Cause[Any],
      _: FiberRefs,
      _: List[LogSpan],
      _: Map[String, String]
    ) => true

  /**
   * Returns a filter which accept logs when the log level satisfies the specified predicate
   */
  def logLevel(predicate: LogLevel => Boolean): LogFilter[Any] =
    (
      _: Trace,
      _: FiberId,
      logLevel: LogLevel,
      _: () => Any,
      _: Cause[Any],
      _: FiberRefs,
      _: List[LogSpan],
      _: Map[String, String]
    ) => predicate(logLevel)

  /**
   * Returns a filter which accept logs when the log level priority is higher then given one
   */
  def logLevel(rootLevel: LogLevel): LogFilter[Any] =
    logLevel(_ >= rootLevel)

  /**
   * Defines a filter from a list of log-levels specified per tree node
   *
   * Example:
   *
   * {{{
   *   val filter =
   *     logLevelAndName(
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
  def logLevelByName(rootLevel: LogLevel, mappings: (String, LogLevel)*): LogFilter[Any] =
    logLevelByGroup(rootLevel, LogGroup.loggerNameAndLevel, mappings: _*)

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
  def logLevelByGroup(
    rootLevel: LogLevel,
    group: LogGroup[(String, LogLevel)],
    mappings: (String, LogLevel)*
  ): LogFilter[Any] =
    logLevelByGroupTree(buildLogFilterTree(rootLevel, mappings), group)

  private def logLevelByGroupTree(
    root: LevelNode,
    group: LogGroup[(String, LogLevel)]
  ): LogFilter[Any] =
    (trace, _, level, _, _, context, _, annotations) => {
      val loggerGroup    = group(trace, level, context, annotations)
      val loggerNames    = loggerGroup._1.split('.').toList
      val loggerLogLevel = findMostSpecificLogLevel(loggerNames, root)
      loggerGroup._2 >= loggerLogLevel
    }

  private def buildLogFilterTree(rootLevel: LogLevel, mappings: Seq[(String, LogLevel)]): LevelNode = {
    def add(tree: LevelNode, names: List[String], level: LogLevel): LevelNode =
      names match {
        case Nil               =>
          tree.copy(logLevel = level)
        case name :: remaining =>
          tree.children.get(name) match {
            case Some(subtree) =>
              tree.copy(
                children = tree.children.updated(name, add(subtree, remaining, level))
              )
            case None          =>
              tree.copy(
                children = tree.children + (name -> add(
                  LevelNode(tree.logLevel, Map.empty),
                  remaining,
                  level
                ))
              )
          }
      }

    mappings.foldLeft(
      LevelNode(rootLevel, Map.empty)
    ) { case (tree, (name, logLevel)) =>
      val nameList = name.split('.').toList
      add(tree, nameList, logLevel)
    }
  }

  @tailrec
  private def findMostSpecificLogLevel(names: List[String], currentNode: LevelNode): LogLevel =
    names match {
      case next :: remaining =>
        currentNode.children.get(next) match {
          case Some(nextNode) =>
            findMostSpecificLogLevel(remaining, nextNode)
          case None           =>
            currentNode.logLevel
        }
      case Nil               =>
        currentNode.logLevel
    }

  implicit class ZLoggerLogFilterOps[M, O](logger: zio.ZLogger[M, O]) {

    /**
     * Returns a version of logger that only logs messages when the `LogFilter` conditions are met
     */
    def filter(filter: LogFilter[M]): zio.ZLogger[M, Option[O]] = filter.filter(logger)
  }

}
