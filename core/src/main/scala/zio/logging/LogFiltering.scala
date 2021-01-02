package zio.logging

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
      val loggerName = ctx.get(LogAnnotation.Name).flatMap(_.split('.'))
      val logLevel   = findMostSpecificLogLevel(loggerName, root)
      ctx.get(LogAnnotation.Level) >= logLevel
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
