package zio.logging

import zio.Config.Error
import zio.{ Chunk, Config, LogLevel }

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
case class LogLevelByNameFilterConfig(rootLevel: LogLevel, mappings: Map[String, LogLevel])

object LogLevelByNameFilterConfig {

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

  private[logging] def logLevelValue(value: String): Either[Error.InvalidData, LogLevel] =
    logLevelMapping.get(value.toUpperCase) match {
      case Some(l) => Right(l)
      case None    => Left(Config.Error.InvalidData(Chunk.empty, s"Expected a LogLevel, but found ${value}"))
    }

  val config: Config[LogLevelByNameFilterConfig] = {
    val rootLevelConfig = Config.string("ROOT_LEVEL").mapOrFail(logLevelValue)

    val mappingsConfig = Config.table("MAPPINGS", Config.string.mapOrFail(logLevelValue))

    (rootLevelConfig ++ mappingsConfig).map { case (rootLevel, mappings) =>
      LogLevelByNameFilterConfig(rootLevel, mappings)
    }
  }
}
