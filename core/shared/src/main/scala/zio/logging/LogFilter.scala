package zio.logging

import zio.{ Cause, FiberId, FiberRefs, LogLevel, LogSpan, Trace, ZLogger }

import scala.annotation.tailrec

/**
 * A `LogFilter` represents function/conditions for log filtering
 */
trait LogFilter[M] { self =>

  def apply(
    trace: Trace,
    fiberId: FiberId,
    logLevel: LogLevel,
    message: () => M,
    cause: Cause[Any],
    context: FiberRefs,
    spans: List[LogSpan],
    annotations: Map[String, String]
  ): Boolean

  /**
   * The alphanumeric version of the `&&` operator.
   */
  final def and(other: LogFilter[M]): LogFilter[M] =
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
  final def &&(other: LogFilter[M]): LogFilter[M] = and(other)

  /**
   * The alphanumeric version of the `||` operator.
   */
  final def or(other: LogFilter[M]): LogFilter[M] =
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
  final def ||(other: LogFilter[M]): LogFilter[M] = or(other)

  /**
   * The alphanumeric version of the `!` operator.
   */
  final def not: LogFilter[M] = (
    trace: Trace,
    fiberId: FiberId,
    logLevel: LogLevel,
    message: () => M,
    cause: Cause[Any],
    context: FiberRefs,
    spans: List[LogSpan],
    annotations: Map[String, String]
  ) => !self(trace, fiberId, logLevel, message, cause, context, spans, annotations)

  /**
   * Returns a new log filter with negated result
   */
  final def unary_! : LogFilter[M] = self.not

  final def cacheWith[A](group: LogGroup[A]): LogFilter[M] =
    new LogFilter[M] {
      private val cache = new java.util.concurrent.ConcurrentHashMap[A, Boolean]()

      override def apply(
        trace: Trace,
        fiberId: FiberId,
        logLevel: LogLevel,
        message: () => M,
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

  def filter[O](logger: zio.ZLogger[M, O]): zio.ZLogger[M, Option[O]] =
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
  def acceptAll[M]: LogFilter[M] =
    (
      _: Trace,
      _: FiberId,
      _: LogLevel,
      _: () => M,
      _: Cause[Any],
      _: FiberRefs,
      _: List[LogSpan],
      _: Map[String, String]
    ) => true

  /**
   * Returns a filter which accept logs when the log level satisfies the specified predicate
   */
  def logLevel[M](predicate: LogLevel => Boolean): LogFilter[M] =
    (
      _: Trace,
      _: FiberId,
      logLevel: LogLevel,
      _: () => M,
      _: Cause[Any],
      _: FiberRefs,
      _: List[LogSpan],
      _: Map[String, String]
    ) => predicate(logLevel)

  /**
   * Returns a filter which accept logs when the log level priority is higher then given one
   */
  def logLevel[M](rootLevel: LogLevel): LogFilter[M] =
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
  def logLevelByName[M](rootLevel: LogLevel, mappings: (String, LogLevel)*): LogFilter[M] =
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
  def logLevelByGroup[M](
    rootLevel: LogLevel,
    group: LogGroup[(String, LogLevel)],
    mappings: (String, LogLevel)*
  ): LogFilter[M] =
    logLevelByGroupTree(buildLogFilterTree(rootLevel, mappings), group)

  private def logLevelByGroupTree[M](
    root: LevelNode,
    group: LogGroup[(String, LogLevel)]
  ): LogFilter[M] =
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
