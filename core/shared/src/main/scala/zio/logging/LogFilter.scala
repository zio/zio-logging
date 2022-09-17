package zio.logging

import zio.stm.{ TMap, ZSTM }
import zio.{ Cause, FiberId, FiberRefs, LogLevel, LogSpan, Trace, ZLogger }

import scala.annotation.tailrec

trait LogFilter { self =>

  def apply(
    trace: Trace,
    logLevel: LogLevel,
    context: FiberRefs,
    annotations: Map[String, String]
  ): Boolean

  final def and(other: LogFilter): LogFilter =
    (trace: Trace, logLevel: LogLevel, context: FiberRefs, annotations: Map[String, String]) =>
      self(trace, logLevel, context, annotations) && other(trace, logLevel, context, annotations)

  final def &&(other: LogFilter): LogFilter = and(other)

  final def or(other: LogFilter): LogFilter =
    (trace: Trace, logLevel: LogLevel, context: FiberRefs, annotations: Map[String, String]) =>
      self(trace, logLevel, context, annotations) || other(trace, logLevel, context, annotations)

  final def ||(other: LogFilter): LogFilter = or(other)

  final def not: LogFilter = (trace: Trace, logLevel: LogLevel, context: FiberRefs, annotations: Map[String, String]) =>
    !self(trace, logLevel, context, annotations)
}

object LogFilter {

  final case class LogFilterNode(logLevel: LogLevel, children: Map[String, LogFilterNode])

  private val loggerNameDefault: (Trace, FiberRefs, Map[String, String]) => String = (trace, _, _) =>
    getLoggerName()(trace)

  val acceptAll: LogFilter =
    (_: Trace, _: LogLevel, _: FiberRefs, _: Map[String, String]) => true

  def logLevel(rootLevel: LogLevel): LogFilter =
    (_: Trace, logLevel: LogLevel, _: FiberRefs, _: Map[String, String]) => logLevel >= rootLevel

  /**
   * Defines a filter function from a list of log-levels specified per tree node
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
   * will use the `Debug` log level for everything except for log events with the name
   * annotation prefixed by either `List("io", "netty")` or `List("io", "grpc", "netty")`.
   *
   * @param rootLevel Minimum log level for the root node
   * @param mappings  List of mappings, nesting defined by dot-separated strings
   * @return A filter function for customizing appenders
   */
  def logLevelAndName(rootLevel: LogLevel, mappings: (String, LogLevel)*): LogFilter =
    logLevelAndName(rootLevel, loggerNameDefault, mappings: _*)

  def logLevelAndName(
    rootLevel: LogLevel,
    loggerName: (Trace, FiberRefs, Map[String, String]) => String,
    mappings: (String, LogLevel)*
  ): LogFilter =
    logLevelAndNameTree(buildLogFilterTree(rootLevel, mappings), loggerName)

  private def logLevelAndNameTree(
    root: LogFilterNode,
    loggerName: (Trace, FiberRefs, Map[String, String]) => String
  ): LogFilter =
    (trace, level, context, annotations) => {
      val loggerNames    = loggerName(trace, context, annotations).split('.').toList
      val loggerLogLevel = findMostSpecificLogLevel(loggerNames, root)
      level >= loggerLogLevel
    }

  type LogFilterCache = TMap[(List[String], LogLevel), Boolean]

  def cachedLogLevelAndName(
    cache: LogFilterCache,
    rootLevel: LogLevel,
    mappings: (String, LogLevel)*
  ): LogFilter =
    cachedLogLevelAndName(cache, rootLevel, loggerNameDefault, mappings: _*)

  def cachedLogLevelAndName(
    cache: LogFilterCache,
    rootLevel: LogLevel,
    loggerName: (Trace, FiberRefs, Map[String, String]) => String,
    mappings: (String, LogLevel)*
  ): LogFilter =
    cachedLogLevelAndNameTree(cache, buildLogFilterTree(rootLevel, mappings), loggerName)

  private def cachedLogLevelAndNameTree(
    cache: LogFilterCache,
    root: LogFilterNode,
    loggerName: (Trace, FiberRefs, Map[String, String]) => String
  ): LogFilter =
    (trace, level, context, annotations) => {
      val loggerNames = loggerName(trace, context, annotations).split('.').toList
      val key         = (loggerNames, level)
      zio.Unsafe.unsafe { implicit u =>
        zio.Runtime.default.unsafe.run {
          val stm = for {
            cached <- cache.get(key)
            result <- cached match {
                        case Some(value) =>
                          ZSTM.succeed(value)
                        case None        =>
                          val mostSpecificLogLevel = findMostSpecificLogLevel(loggerNames, root)
                          val answer               = level >= mostSpecificLogLevel
                          cache.put(key, answer).as(answer)
                      }
          } yield result
          stm.commit
        }.getOrThrowFiberFailure()
      }
    }

  def cachedLogLevelAndName(
    rootLevel: LogLevel,
    mappings: (String, LogLevel)*
  ): LogFilter =
    cachedLogLevelAndName(rootLevel, loggerNameDefault, mappings: _*)

  def cachedLogLevelAndName(
    rootLevel: LogLevel,
    loggerName: (Trace, FiberRefs, Map[String, String]) => String,
    mappings: (String, LogLevel)*
  ): LogFilter =
    cachedLogLevelAndNameTree(buildLogFilterTree(rootLevel, mappings), loggerName)

  private def cachedLogLevelAndNameTree(
    root: LogFilterNode,
    loggerName: (Trace, FiberRefs, Map[String, String]) => String
  ): LogFilter = {
    val cache = new java.util.concurrent.ConcurrentHashMap[(List[String], LogLevel), Boolean]()
    (trace, level, context, annotations) => {
      val loggerNames                   = loggerName(trace, context, annotations).split('.').toList
      val key: (List[String], LogLevel) = (loggerNames, level)

      cache.computeIfAbsent(
        key,
        _ => {
          val mostSpecificLogLevel = findMostSpecificLogLevel(loggerNames, root)
          val answer               = level >= mostSpecificLogLevel
          answer
        }
      )
    }
  }

  private def buildLogFilterTree(rootLevel: LogLevel, mappings: Seq[(String, LogLevel)]): LogFilterNode = {
    def add(tree: LogFilterNode, names: List[String], level: LogLevel): LogFilterNode =
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
                  LogFilterNode(tree.logLevel, Map.empty),
                  remaining,
                  level
                ))
              )
          }
      }

    mappings.foldLeft(
      LogFilterNode(rootLevel, Map.empty)
    ) { case (tree, (name, logLevel)) =>
      val nameList = name.split('.').toList
      add(tree, nameList, logLevel)
    }
  }

  @tailrec
  private def findMostSpecificLogLevel(names: List[String], currentNode: LogFilterNode): LogLevel =
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
    def filter(filter: LogFilter): zio.ZLogger[M, Option[O]] =
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
          if (filter(trace, logLevel, context, annotations)) {
            Some(logger(trace, fiberId, logLevel, message, cause, context, spans, annotations))
          } else None
      }
  }

}
