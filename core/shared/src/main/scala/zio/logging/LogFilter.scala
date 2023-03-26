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

import zio.{ Cause, Chunk, Config, FiberId, FiberRefs, LogLevel, LogSpan, Trace, ZLogger }

import scala.annotation.tailrec

/**
 * A `LogFilter` represents function/conditions for log filtering
 */
sealed trait LogFilter[-Message] { self =>

  type Value

  def group: LogGroup[Message, Value]

  def predicate(value: Value): Boolean

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
    val v = group(trace, fiberId, logLevel, message, cause, context, spans, annotations)

    predicate(v)
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
    LogFilter[M, (self.Value, other.Value)](
      self.group ++ other.group,
      v => {
        val (v1, v2) = v

        self.predicate(v1) && other.predicate(v2)
      }
    )

  /**
   * Returns a new log filter with cached results
   */
  final def cached: LogFilter[Message] = {
    val cache = new java.util.concurrent.ConcurrentHashMap[Value, Boolean]()
    LogFilter[Message, self.Value](
      self.group,
      v =>
        cache.computeIfAbsent(
          v,
          _ => self.predicate(v)
        )
    )
  }

  final def contramap[M](f: M => Message): LogFilter[M] = LogFilter[M, self.Value](
    group.contramap(f),
    self.predicate
  )

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

  /**
   * The alphanumeric version of the `!` operator.
   */
  final def not: LogFilter[Message] =
    LogFilter[Message, self.Value](self.group, v => !self.predicate(v))

  /**
   * The alphanumeric version of the `||` operator.
   */
  final def or[M <: Message](other: LogFilter[M]): LogFilter[M] =
    LogFilter[M, (self.Value, other.Value)](
      self.group ++ other.group,
      v => {
        val (v1, v2) = v

        self.predicate(v1) || other.predicate(v2)
      }
    )

  /**
   * Returns a new log filter with negated result
   */
  final def unary_! : LogFilter[Message] = self.not

}

object LogFilter {

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
   */
  final case class LogLevelByNameConfig(rootLevel: LogLevel, mappings: Map[String, LogLevel])

  object LogLevelByNameConfig {

    private[logging] val logLevelMapping: Map[String, LogLevel] = Map(
      LogLevel.All.label     -> LogLevel.All,
      LogLevel.Trace.label   -> LogLevel.Trace,
      LogLevel.Debug.label   -> LogLevel.Debug,
      LogLevel.Info.label    -> LogLevel.Info,
      LogLevel.Warning.label -> LogLevel.Warning,
      LogLevel.Error.label   -> LogLevel.Error,
      LogLevel.Fatal.label   -> LogLevel.Fatal,
      LogLevel.None.label    -> LogLevel.None
    )

    private[logging] def logLevelValue(value: String): Either[Config.Error.InvalidData, LogLevel] =
      logLevelMapping.get(value.toUpperCase) match {
        case Some(v) => Right(v)
        case None    => Left(Config.Error.InvalidData(Chunk.empty, s"Expected a LogLevel, but found ${value}"))
      }

    val config: Config[LogLevelByNameConfig] = {
      val rootLevelConfig = Config.string.mapOrFail(logLevelValue).nested("rootLevel").withDefault(LogLevel.Info)
      val mappingsConfig  = Config.table("mappings", Config.string.mapOrFail(logLevelValue)).withDefault(Map.empty)

      (rootLevelConfig ++ mappingsConfig).map { case (rootLevel, mappings) =>
        LogLevelByNameConfig(rootLevel, mappings)
      }
    }
  }

  def apply[M, V](
    group0: LogGroup[M, V],
    predicate0: V => Boolean
  ): LogFilter[M] = new LogFilter[M] {
    override type Value = V
    override def group: LogGroup[M, V]            = group0
    override def predicate(value: Value): Boolean = predicate0(value)
  }

  def apply[M](
    group0: LogGroup[M, Boolean]
  ): LogFilter[M] = apply[M, Boolean](group0, identity)

  /**
   * Log filter which accept all logs (logs are not filtered)
   */
  val acceptAll: LogFilter[Any] = apply[Any](LogGroup.constant(true))

  /**
   * Log filter which accept where cause is non empty
   */
  val causeNonEmpty: LogFilter[Any] = apply[Any, Cause[Any]](LogGroup.cause, !_.isEmpty)

  /**
   * Returns a filter which accept logs when the log level satisfies the specified predicate
   */
  def logLevel(predicate: LogLevel => Boolean): LogFilter[Any] = apply[Any, LogLevel](
    LogGroup.logLevel,
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
      group ++ LogGroup.logLevel,
      v => {
        val (loggerGroup, level) = v
        val groupingLogLevel     = groupings.collectFirst {
          case (groupingGroup, groupingLevel) if matcher(loggerGroup, groupingGroup) => groupingLevel
        }.getOrElse(rootLevel)

        level >= groupingLogLevel
      }
    )

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
    val mappingsSorted = mappings.map(splitNameByDotAndLevel.tupled).sorted(nameLevelOrdering)
    val nameGroup      = group.map(splitNameByDot)

    @tailrec
    def globStarCompare(l: List[String], m: List[String]): Boolean =
      (l, m) match {
        case (_, Nil)           => true
        case (Nil, _)           => false
        case (l @ (_ :: ls), m) =>
          // try a regular, routesCompare or check if skipping paths (globstar pattern) results in a matching path
          l.startsWith(m) || compareRoutes(l, m) || globStarCompare(ls, m)
      }

    @tailrec
    def anystringCompare(l: String, m: List[String]): Boolean = m match {
      case mh :: ms =>
        val startOfMh = l.indexOfSlice(mh)
        if (startOfMh >= 0) anystringCompare(l.drop(startOfMh + mh.size), ms)
        else false
      case Nil      => l.isEmpty()
    }

    @tailrec
    def compareRoutes(l: List[String], m: List[String]): Boolean =
      (l, m) match {
        case (_, Nil)                                  => true
        case (Nil, _)                                  => false
        case (_ :: ls, "*" :: ms)                      => compareRoutes(ls, ms)
        case (l, "**" :: ms)                           => globStarCompare(l, ms)
        case (lh :: ls, mh :: ms) if !mh.contains("*") =>
          lh == mh && compareRoutes(ls, ms)
        case (l @ (lh :: ls), m @ (mh :: ms))          =>
          anystringCompare(lh, mh.split('*').toList) && compareRoutes(ls, ms)
      }

    logLevelByGroup[M, List[String]](
      rootLevel,
      nameGroup,
      (l, m) => l.startsWith(m) || compareRoutes(l, m),
      mappingsSorted: _*
    )
  }

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
   * Logger name is extracted from log annotation or [[Trace]], see: [[LogGroup.loggerName]]
   *
   * @param rootLevel Minimum log level for the root node
   * @param mappings  List of mappings, nesting defined by dot-separated strings
   * @return A filter for log filtering based on log level and name
   */
  def logLevelByName[M](rootLevel: LogLevel, mappings: (String, LogLevel)*): LogFilter[M] =
    logLevelByGroup[M](rootLevel, LogGroup.loggerName, mappings: _*)

  def logLevelByGroup[M](group: LogGroup[M, String], config: LogLevelByNameConfig): LogFilter[M] =
    logLevelByGroup[M](config.rootLevel, group, config.mappings.toList: _*)

  def logLevelByName[M](config: LogLevelByNameConfig): LogFilter[M] =
    logLevelByGroup[M](LogGroup.loggerName, config)

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
            if (xFirst.contains('*') || yFirst.contains('*')) {
              if (Set("**", "*").contains(xFirst)) 1
              else if (Set("**", "*").contains(yFirst)) -1
              else
                compareNames(xFirst.split('*').toList.filter(_.nonEmpty), yFirst.split('*').toList.filter(_.nonEmpty))
            } else r
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
