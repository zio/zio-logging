package zio.logging

import zio.stm.{ TMap, ZSTM }
import zio.{ Cause, FiberId, FiberRefs, LogLevel, LogSpan, Trace, ZLogger }

import scala.annotation.tailrec

object LogFiltering {
  final case class LogFilterNode(logLevel: LogLevel, children: Map[String, LogFilterNode])

  type Filter = (Trace, LogLevel) => Boolean

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
    filterByTree(buildLogFilterTree(rootLevel, mappings))

  def filterByTree(root: LogFilterNode): Filter =
    (trace, logLevel) => {
      val loggerName     = getLoggerNames(trace)
      val loggerLogLevel = findMostSpecificLogLevel(loggerName, root)
      logLevel >= loggerLogLevel
    }

  def filterBy(rootLevel: LogLevel): Filter =
    (_, logLevel) => logLevel >= rootLevel

  type FilterCache = TMap[(List[String], LogLevel), Boolean]

  def cachedFilterBy(
    cache: FilterCache,
    rootLevel: LogLevel,
    mappings: (String, LogLevel)*
  ): Filter =
    cachedFilterByTree(cache, buildLogFilterTree(rootLevel, mappings))

  def cachedFilterByTree(cache: FilterCache, root: LogFilterNode): Filter =
    (trace, level) => {
      val loggerName = getLoggerNames(trace)
      val key        = (loggerName, level)
      zio.Unsafe.unsafe { implicit u =>
        zio.Runtime.default.unsafe.run {
          val stm = for {
            cached <- cache.get(key)
            result <- cached match {
                        case Some(value) =>
                          ZSTM.succeed(value)
                        case None        =>
                          val mostSpecificLogLevel = findMostSpecificLogLevel(loggerName, root)
                          val answer               = level >= mostSpecificLogLevel
                          cache.put(key, answer).as(answer)
                      }
          } yield result
          stm.commit
        }.getOrThrowFiberFailure()
      }
    }

  private def getLoggerNames(trace: Trace): List[String] = {
    val name = getLoggerName()(trace)
    name.split('.').toList
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
          if (filter(trace, logLevel)) {
            Some(logger(trace, fiberId, logLevel, message, cause, context, spans, annotations))
          } else None
      }

  }
}
