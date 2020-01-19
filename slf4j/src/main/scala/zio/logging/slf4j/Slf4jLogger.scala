package zio.logging.slf4j

import org.slf4j.LoggerFactory
import zio.logging._
import zio.{ UIO, ZIO }

object Slf4jLogger {

  private def logger(name: String) =
    ZIO.effectTotal(
      LoggerFactory.getLogger(
        name
      )
    )

  def make(logFormat: (LogContext, => String) => String): UIO[Logging[Any]] =
    Logging.make { (context, line) =>
      val loggerName = context.get(LogAnnotation.Name) match {
        case Nil   => Logger.classNameForLambda(line).getOrElse("ZIO.defaultLogger")
        case names => LogAnnotation.Name.render(names)
      }
      logger(loggerName).map(slf4jLogger =>
        context.get(LogAnnotation.Level).level match {
          case LogLevel.Off.level   => ()
          case LogLevel.Debug.level => slf4jLogger.debug(logFormat(context, line))
          case LogLevel.Trace.level => slf4jLogger.trace(logFormat(context, line))
          case LogLevel.Info.level  => slf4jLogger.info(logFormat(context, line))
          case LogLevel.Error.level => slf4jLogger.error(logFormat(context, line))
          case LogLevel.Fatal.level => slf4jLogger.error(logFormat(context, line))
        }
      )
    }
}
