package zio.logging.slf4j

import org.slf4j.LoggerFactory
import zio.logging._
import zio.{UIO, ZIO}

object Slf4jLogger {

  private def logger(lambda: => AnyRef) =
    ZIO.effectTotal(
      LoggerFactory.getLogger(
        Logger.classNameForLambda(lambda).getOrElse("ZIO.defaultLogger")
      )
    )

  def make(level: LogLevel, logFormat: (LogContext, => String) => String): UIO[Logger] = {
    Logger.make(level, (context, line) =>
      logger(line).map(slf4jLogger =>
        context.get(LogAnnotation.Level).level match {
          case LogLevel.Off.level   => ()
          case LogLevel.Debug.level => slf4jLogger.debug(logFormat(context, line))
          case LogLevel.Trace.level => slf4jLogger.trace(logFormat(context, line))
          case LogLevel.Info.level  => slf4jLogger.info(logFormat(context, line))
          case LogLevel.Error.level => slf4jLogger.error(logFormat(context, line))
          case LogLevel.Fatal.level => slf4jLogger.error(logFormat(context, line))
        }
      )
    )
  }
}
