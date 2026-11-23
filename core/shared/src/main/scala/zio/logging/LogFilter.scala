/*
 * Copyright 2019-2026 John A. De Goes and the ZIO Contributors
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

import zio.prelude.Equal
import zio.{ Cause, Config, FiberId, FiberRefs, LogLevel, LogSpan, NonEmptyChunk, Trace, ZIO }

import scala.annotation.tailrec

/**
 * A `LogFilter` decides whether a log event is written. It has two parts: a `LogGroup` that extracts one
 * value from the event, such as its level or logger name, and a predicate that tests that value. Combine
 * filters with `&&`, `||` and `!`, and put a filter in front of a logger with `filter`.
 *
 * {{{
 * import zio.LogLevel
 * import zio.logging.LogFilter
 *
 * // WARN and above, plus DEBUG and above for loggers under com.example
 * val filter: LogFilter[String] =
 *   LogFilter.logLevelByName[String](LogLevel.Warning, "com.example" -> LogLevel.Debug)
 *
 * // only events that carry a cause
 * val failures: LogFilter[Any] = LogFilter.causeNonEmpty
 * val both: LogFilter[String]  = filter && failures
 * }}}
 *
 * @see [[https://zio.dev/zio-logging/log-filter/ Log Filter]]
 * @see [[https://zio.dev/zio-logging/reference-log-filter/ LogFilter and LogGroup Reference]]
 */
sealed trait LogFilter[-Message] { self =>

  /** The type of the value that `group` extracts and `predicate` tests. */
  type Value

  /** Extracts the value that this filter tests from a log event. */
  def group: LogGroup[Message, Value]

  /** Returns `true` when an event with this group value passes the filter. */
  def predicate(value: Value): Boolean

  /**
   * Runs `group` on the log event, then `predicate` on the result. Returns `true` when the event passes.
   */
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
   * Returns a new log filter which accepts an event only when both this and the given log filter accept it.
   */
  final def &&[M <: Message](other: LogFilter[M]): LogFilter[M] = and(other)

  /**
   * Returns a new log filter which accepts an event when this or the given log filter accepts it.
   */
  final def ||[M <: Message](other: LogFilter[M]): LogFilter[M] = or(other)

  /**
   * The alphanumeric version of the `&&` operator.
   */
  final def and[M <: Message](other: LogFilter[M]): LogFilter[M] =
    LogFilter.AndFilter(self, other)

  /**
   * Returns a new log filter with cached results: the predicate runs once for each distinct group value, and
   * later events with the same value reuse the result. The group itself still runs for every event.
   */
  final def cached: LogFilter[Message] =
    self match {
      case LogFilter.CachedFilter(filter) => LogFilter.CachedFilter(filter)
      case _                              => LogFilter.CachedFilter(self)
    }

  /**
   * Returns a filter for messages of type `M`, which converts each message with `f` before this filter's
   * group reads it.
   */
  final def contramap[M](f: M => Message): LogFilter[M] = LogFilter[M, self.Value](
    group.contramap(f),
    self.predicate
  )

  /**
   * Returns a version of logger that only logs messages when this filter is satisfied
   */
  final def filter[M <: Message, O](logger: zio.ZLogger[M, O]): zio.ZLogger[M, Option[O]] = FilteredLogger(logger, self)

  /**
   * The alphanumeric version of the `!` operator.
   */
  final def not: LogFilter[Message] =
    LogFilter.NotFilter(self)

  /**
   * The alphanumeric version of the `||` operator.
   */
  final def or[M <: Message](other: LogFilter[M]): LogFilter[M] =
    LogFilter.OrFilter(self, other)

  /**
   * Returns a new log filter with negated result
   */
  final def unary_! : LogFilter[Message] = self.not

}

/**
 * Constructors for log filters: from a `LogGroup` and a predicate, by log level, and by log level per logger
 * name.
 *
 * @see [[https://zio.dev/zio-logging/log-filter/ Log Filter]]
 */
object LogFilter {

  /**
   * Compares filters by structure: filters combined with `and`, `or`, `not` or `cached` are equal when their
   * parts are equal, and filters built from a `LogLevelByNameConfig` are equal when their configs
   * are equal. Other filters are compared with `==`, so two filters built from separate function values,
   * such as two calls of `LogFilter.logLevel(LogLevel.Info)`, are not equal.
   */
  implicit val equal: Equal[LogFilter[_]] = Equal.make { (l, r) =>
    (l, r) match {
      case (l: GroupPredicateFilter[_, _], r: GroupPredicateFilter[_, _]) => GroupPredicateFilter.equal.equal(l, r)
      case (l: CachedFilter[_], r: CachedFilter[_])                       => CachedFilter.equal.equal(l, r)
      case (l: AndFilter[_], r: AndFilter[_])                             => AndFilter.equal.equal(l, r)
      case (l: OrFilter[_], r: OrFilter[_])                               => OrFilter.equal.equal(l, r)
      case (l: NotFilter[_], r: NotFilter[_])                             => NotFilter.equal.equal(l, r)
      case (l: ConfiguredFilter[_, _], r: ConfiguredFilter[_, _])         => ConfiguredFilter.equal.equal(l, r)
      case (l, r)                                                         => l == r
    }
  }

  private[logging] final case class GroupPredicateFilter[M, V](
    logGroup: LogGroup[M, V],
    valuePredicate: V => Boolean
  ) extends LogFilter[M] {
    override type Value = V

    override def group: LogGroup[M, Value] = logGroup

    override def predicate(value: Value): Boolean = valuePredicate(value)
  }

  private[logging] object GroupPredicateFilter {
    implicit val equal: Equal[GroupPredicateFilter[_, _]] = Equal.default
  }

  private[logging] final case class AndFilter[M](
    first: LogFilter[M],
    second: LogFilter[M]
  ) extends LogFilter[M] {
    override type Value = (first.Value, second.Value)

    override val group: LogGroup[M, Value] = first.group ++ second.group

    override def predicate(value: Value): Boolean = {
      val (v1, v2) = value
      first.predicate(v1) && second.predicate(v2)
    }
  }

  private[logging] object AndFilter {
    implicit val equal: Equal[AndFilter[_]] = Equal.make { (f, s) =>
      LogFilter.equal.equal(f.first, s.first) && LogFilter.equal.equal(f.second, s.second)
    }
  }

  private[logging] final case class OrFilter[M](
    first: LogFilter[M],
    second: LogFilter[M]
  ) extends LogFilter[M] {
    override type Value = (first.Value, second.Value)

    override val group: LogGroup[M, Value] = first.group ++ second.group

    override def predicate(value: Value): Boolean = {
      val (v1, v2) = value
      first.predicate(v1) || second.predicate(v2)
    }
  }

  private[logging] object OrFilter {
    implicit val equal: Equal[OrFilter[_]] = Equal.make { (f, s) =>
      LogFilter.equal.equal(f.first, s.first) && LogFilter.equal.equal(f.second, s.second)
    }
  }

  private[logging] final case class NotFilter[M](
    filter: LogFilter[M]
  ) extends LogFilter[M] {
    override type Value = filter.Value

    override def group: LogGroup[M, Value] = filter.group

    override def predicate(value: Value): Boolean =
      !filter.predicate(value)
  }

  private[logging] object NotFilter {
    implicit val equal: Equal[NotFilter[_]] = Equal.make { (f, s) =>
      LogFilter.equal.equal(f.filter, s.filter)
    }
  }

  private[logging] final case class CachedFilter[M](filter: LogFilter[M]) extends LogFilter[M] {

    private[logging] val cache = new java.util.concurrent.ConcurrentHashMap[filter.Value, Boolean]()

    override type Value = filter.Value

    override def group: LogGroup[M, Value] = filter.group

    override def predicate(value: Value): Boolean =
      cache.computeIfAbsent(
        value,
        _ => filter.predicate(value)
      )

    def clear(): Unit =
      cache.clear()
  }

  private[logging] object CachedFilter {
    implicit val equal: Equal[CachedFilter[_]] = Equal.make { (f, s) =>
      LogFilter.equal.equal(f.filter, s.filter)
    }
  }

  private[logging] final case class ConfiguredFilter[M, C](config: C, make: C => LogFilter[M]) extends LogFilter[M] {
    val filter: LogFilter[M] = make(config)

    override type Value = filter.Value

    override def group: LogGroup[M, Value] = filter.group

    override def predicate(value: Value): Boolean = filter.predicate(value)

    def witConfig(newConfig: C): ConfiguredFilter[M, C] = ConfiguredFilter(newConfig, make)
  }

  private[logging] object ConfiguredFilter {
    implicit val equal: Equal[ConfiguredFilter[_, _]] = Equal.default.contramap(_.config)
  }

  /**
   * The minimum log levels of a filter, per logger name: a root level, and a level for each logger name
   * prefix, nesting defined by dot-separated names. `toFilter` turns it into a filter.
   *
   * Example:
   *
   * {{{
   * import zio.LogLevel
   * import zio.logging.LogFilter
   *
   * val config =
   *   LogFilter.LogLevelByNameConfig(
   *     LogLevel.Debug,
   *     "io.netty"      -> LogLevel.Info,
   *     "io.grpc.netty" -> LogLevel.Info
   *   )
   * val filter: LogFilter[String] = config.toFilter[String]
   * }}}
   *
   * will use the `Debug` log level for everything except for log events with the logger name
   * prefixed by either `List("io", "netty")` or `List("io", "grpc", "netty")`.
   * Logger name is extracted from the logger name annotation, or from the `Trace` of the log call when the
   * annotation is not set; see [[LogGroup.loggerName]].
   *
   * @param rootLevel Minimum log level for the root node
   * @param mappings  List of mappings, nesting defined by dot-separated strings
   * @see [[https://zio.dev/zio-logging/log-filter/ Log Filter]]
   */
  final case class LogLevelByNameConfig(rootLevel: LogLevel, mappings: Map[String, LogLevel]) {

    /** Returns a copy with `rootLevel` as the root level. */
    def withRootLevel(rootLevel: LogLevel): LogLevelByNameConfig =
      LogLevelByNameConfig(rootLevel, mappings)

    /** Returns a copy with the mapping `name -> level` added, replacing any mapping for the same `name`. */
    def withMapping(name: String, level: LogLevel): LogLevelByNameConfig =
      LogLevelByNameConfig(rootLevel, mappings + (name -> level))

    /** Returns a copy whose mappings are `mappings`, replacing all current mappings. */
    def withMappings(mappings: Map[String, LogLevel]): LogLevelByNameConfig =
      LogLevelByNameConfig(rootLevel, mappings)

    /** Builds the filter for this config, the same as `LogFilter.logLevelByName(this)`. */
    def toFilter[M]: LogFilter[M] = LogFilter.logLevelByName(this)
  }

  /**
   * Constructors and the config reader for `LogLevelByNameConfig`.
   */
  object LogLevelByNameConfig {

    /** Creates a config from a root level and `name -> level` pairs. */
    def apply(rootLevel: LogLevel, mappings: (String, LogLevel)*): LogLevelByNameConfig =
      LogLevelByNameConfig(rootLevel, mappings.toMap)

    /** Root level INFO, with no mappings. */
    val default: LogLevelByNameConfig = LogLevelByNameConfig(LogLevel.Info, Map.empty[String, LogLevel])

    /**
     * Reads `rootLevel` (default `INFO`) and the `mappings` table of logger names to levels (default empty).
     *
     * @see [[https://zio.dev/zio-logging/reference-configuration/ Configuration Keys Reference]]
     */
    val config: Config[LogLevelByNameConfig] = {
      val rootLevelConfig = Config.logLevel.nested("rootLevel").withDefault(LogLevel.Info)
      val mappingsConfig  = Config.table("mappings", Config.logLevel).withDefault(Map.empty)

      (rootLevelConfig ++ mappingsConfig).map { case (rootLevel, mappings) =>
        LogLevelByNameConfig(rootLevel, mappings)
      }
    }

    /** Compares configs by their root level and mappings. */
    implicit val equal: Equal[LogLevelByNameConfig] = Equal.default

    /** Reads the config at `configPath` from the `ConfigProvider` of the running effect. */
    def load(configPath: NonEmptyChunk[String]): ZIO[Any, Config.Error, LogLevelByNameConfig] =
      ZIO.config(LogLevelByNameConfig.config.nested(configPath.head, configPath.tail: _*))
  }

  /**
   * Creates a filter that accepts an event when `predicate0` returns `true` for the value that `group0`
   * extracts.
   *
   * {{{
   * import zio.LogLevel
   * import zio.logging.{ LogFilter, LogGroup }
   *
   * val errorsOnly: LogFilter[Any] = LogFilter(LogGroup.logLevel, (level: LogLevel) => level >= LogLevel.Error)
   * }}}
   */
  def apply[M, V](
    group0: LogGroup[M, V],
    predicate0: V => Boolean
  ): LogFilter[M] = GroupPredicateFilter(group0, predicate0)

  /** Creates a filter that accepts an event when `group0` returns `true` for it. */
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
   * Returns a filter which accepts logs whose level is `rootLevel` or higher.
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
   * @param matcher Matcher for log group and groupings, called as `matcher(groupValue, grouping)`
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
   * import zio.LogLevel
   * import zio.logging.{ LogFilter, LogGroup }
   *
   * val filter: LogFilter[Any] =
   *   LogFilter.logLevelByGroup[Any](
   *     LogLevel.Debug,
   *     LogGroup.loggerName,
   *     "io.netty"      -> LogLevel.Info,
   *     "io.grpc.netty" -> LogLevel.Info
   *   )
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

    logLevelByGroup[M, List[String]](
      rootLevel,
      nameGroup,
      nameMatcher,
      mappingsSorted: _*
    )
  }

  /**
   * Defines a filter from a list of log-levels specified per tree node
   *
   * Example:
   *
   * {{{
   * import zio.LogLevel
   * import zio.logging.LogFilter
   *
   * val filter: LogFilter[Any] =
   *   LogFilter.logLevelByName[Any](
   *     LogLevel.Debug,
   *     "io.netty"      -> LogLevel.Info,
   *     "io.grpc.netty" -> LogLevel.Info
   *   )
   * }}}
   *
   * will use the `Debug` log level for everything except for log events with the logger name
   * prefixed by either `List("io", "netty")` or `List("io", "grpc", "netty")`.
   * Logger name is extracted from log annotation or `Trace`, see: [[LogGroup.loggerName]]
   *
   * @param rootLevel Minimum log level for the root node
   * @param mappings  List of mappings, nesting defined by dot-separated strings
   * @return A filter for log filtering based on log level and name
   */
  def logLevelByName[M](rootLevel: LogLevel, mappings: (String, LogLevel)*): LogFilter[M] =
    logLevelByGroup[M](rootLevel, LogGroup.loggerName, mappings: _*)

  /**
   * Same as the `logLevelByGroup` overload that takes `(String, LogLevel)` mappings, with the root level and
   * the mappings read from `config`. Filters built this way compare equal when their configs are equal.
   */
  def logLevelByGroup[M](group: LogGroup[M, String], config: LogLevelByNameConfig): LogFilter[M] =
    ConfiguredFilter[M, LogLevelByNameConfig](
      config,
      config => logLevelByGroup[M](config.rootLevel, group, config.mappings.toList: _*)
    )

  /**
   * Same as the `logLevelByName` overload that takes `(String, LogLevel)` mappings, with the root level and
   * the mappings read from `config`.
   */
  def logLevelByName[M](config: LogLevelByNameConfig): LogFilter[M] =
    logLevelByGroup[M](LogGroup.loggerName, config)

  private[logging] val nameMatcher: (List[String], List[String]) => Boolean = (l, m) => {

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
        case (lh :: ls, mh :: ms)                      =>
          anystringCompare(lh, mh.split('*').toList) && compareRoutes(ls, ms)
      }

    l.startsWith(m) || compareRoutes(l, m)
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
   *  import zio.LogLevel
   *
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
   *  import zio.LogLevel
   *
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
              if (xFirst == "**") 1
              else if (yFirst == "**") -1
              else if (xFirst == "*") 1
              else if (yFirst == "*") -1
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
