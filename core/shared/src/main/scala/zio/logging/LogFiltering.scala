package zio.logging

import zio.stm.{ TMap, ZSTM }
import zio.{ Cause, FiberId, FiberRefs, LogLevel, LogSpan, Trace, ZLogger }

import scala.annotation.tailrec

object LogFiltering {
  final case class LogFilterNode(logLevel: LogLevel, children: Map[String, LogFilterNode])

  type Filter = (Trace, LogLevel, FiberRefs, Map[String, String]) => Boolean

  private val loggerNameDefault: (Trace, FiberRefs, Map[String, String]) => String = (trace, _, _) =>
    getLoggerName()(trace)

  val default: Filter = (_, _, _, _) => true

  /**
   * Defines a filter function from a list of log-levels specified per tree node
   *
   * Example:
   *
   * {{{
   *   val filter =
   *     filterBy(
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
   * @param mappings List of mappings, nesting defined by dot-separated strings
   * @return A filter function for customizing appenders
   */
  def filterBy(rootLevel: LogLevel, mappings: (String, LogLevel)*): Filter =
    filterBy(rootLevel, loggerNameDefault, mappings: _*)

  def filterBy(
    rootLevel: LogLevel,
    loggerName: (Trace, FiberRefs, Map[String, String]) => String,
    mappings: (String, LogLevel)*
  ): Filter =
    filterByTree(buildLogFilterTree(rootLevel, mappings), loggerName)

  private def filterByTree(root: LogFilterNode, loggerName: (Trace, FiberRefs, Map[String, String]) => String): Filter =
    (trace, level, context, annotations) => {
      val loggerNames    = loggerName(trace, context, annotations).split('.').toList
      val loggerLogLevel = findMostSpecificLogLevel(loggerNames, root)
      level >= loggerLogLevel
    }

  def filterBy(rootLevel: LogLevel): Filter =
    (_, level, _, _) => level >= rootLevel

  type FilterCache = TMap[(List[String], LogLevel), Boolean]

  def cachedFilterBy(
    cache: FilterCache,
    rootLevel: LogLevel,
    mappings: (String, LogLevel)*
  ): Filter =
    cachedFilterBy(cache, rootLevel, loggerNameDefault, mappings: _*)

  def cachedFilterBy(
    cache: FilterCache,
    rootLevel: LogLevel,
    loggerName: (Trace, FiberRefs, Map[String, String]) => String,
    mappings: (String, LogLevel)*
  ): Filter =
    cachedFilterByTree(cache, buildLogFilterTree(rootLevel, mappings), loggerName)

  private def cachedFilterByTree(
    cache: FilterCache,
    root: LogFilterNode,
    loggerName: (Trace, FiberRefs, Map[String, String]) => String
  ): Filter =
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

  def cachedFilterBy(
    rootLevel: LogLevel,
    mappings: (String, LogLevel)*
  ): Filter =
    cachedFilterBy(rootLevel, loggerNameDefault, mappings: _*)

  def cachedFilterBy(
    rootLevel: LogLevel,
    loggerName: (Trace, FiberRefs, Map[String, String]) => String,
    mappings: (String, LogLevel)*
  ): Filter =
    cachedFilterByTree(buildLogFilterTree(rootLevel, mappings), loggerName)

  private def cachedFilterByTree(
    root: LogFilterNode,
    loggerName: (Trace, FiberRefs, Map[String, String]) => String
  ): Filter = {
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

  implicit class ZLoggerFilterOps[M, O](logger: zio.ZLogger[M, O]) {
    def filter(filter: Filter): zio.ZLogger[M, Option[O]] =
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
