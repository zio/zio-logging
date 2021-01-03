package zio.logging

import zio.stm.{ TMap, ZSTM }
import zio.UIO

import scala.annotation.tailrec

object LogFiltering {
  case class LogFilterNode(logLevel: LogLevel, children: Map[String, LogFilterNode])

  /**
   * Defines a filter function from a list of log-levels specified per tree node
   *
   * Example:
   *
   * {{{
   *   val filter =
   *     filterBy[String](
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
  def filterBy[A](rootLevel: LogLevel, mappings: (String, LogLevel)*): (LogContext, => A) => Boolean =
    filterByTree(buildLogFilterTree(rootLevel, mappings))

  def filterByTree[A](root: LogFilterNode): (LogContext, => A) => Boolean =
    (ctx, _) => {
      val loggerName = ctx.get(LogAnnotation.Name)
      val logLevel   = findMostSpecificLogLevel(loggerName, root)
      ctx.get(LogAnnotation.Level) >= logLevel
    }

  type FilterCache = TMap[(List[String], LogLevel), Boolean]

  def cachedFilterBy[A](
    cache: FilterCache,
    rootLevel: LogLevel,
    mappings: (String, LogLevel)*
  ): (LogContext, => A) => UIO[Boolean] =
    cachedFilterByTree(cache, buildLogFilterTree(rootLevel, mappings))

  def cachedFilterByTree[A](cache: FilterCache, root: LogFilterNode): (LogContext, => A) => UIO[Boolean] =
    (ctx, _) => {
      val loggerName = ctx.get(LogAnnotation.Name)
      val level      = ctx.get(LogAnnotation.Level)
      val key        = (loggerName, level)
      val stm        = for {
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

}
