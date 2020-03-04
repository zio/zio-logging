package zio.logging.slf4j

import org.slf4j.LoggerFactory
import zio.internal.Tracing
import zio.internal.stacktracer.Tracer
import zio.internal.stacktracer.ZTraceElement.{ NoLocation, SourceLocation }
import zio.internal.stacktracer.impl.AkkaLineNumbersTracer
import zio.internal.tracing.TracingConfig
import zio.logging._
import zio.{ ZIO, ZLayer }

object Slf4jLogger {

  private val tracing = Tracing(Tracer.globallyCached(new AkkaLineNumbersTracer), TracingConfig.enabled)

  private def classNameForLambda(lambda: => AnyRef) =
    tracing.tracer.traceLocation(() => lambda) match {
      case SourceLocation(_, clazz, _, _) => Some(clazz)
      case NoLocation(_)                  => None
    }

  private def logger(name: String) =
    ZIO.effectTotal(
      LoggerFactory.getLogger(
        name
      )
    )

  def makeWithName(name: String)(logFormat: (LogContext, => String) => String): ZLayer[Any, Nothing, Logging] =
    make((context, line) => logFormat(context.annotate(LogAnnotation.Name, name :: Nil), line))

  def make(logFormat: (LogContext, => String) => String): ZLayer[Any, Nothing, Logging] =
    Logging.make { (context, line) =>
      val loggerName = context.get(LogAnnotation.Name) match {
        case Nil   => classNameForLambda(line).getOrElse("ZIO.defaultLogger")
        case names => LogAnnotation.Name.render(names)
      }
      logger(loggerName).map(slf4jLogger =>
        context.get(LogAnnotation.Level).level match {
          case LogLevel.Off.level   => ()
          case LogLevel.Debug.level => slf4jLogger.debug(logFormat(context, line))
          case LogLevel.Trace.level => slf4jLogger.trace(logFormat(context, line))
          case LogLevel.Info.level  => slf4jLogger.info(logFormat(context, line))
          case LogLevel.Warn.level  => slf4jLogger.warn(logFormat(context, line))
          case LogLevel.Error.level => slf4jLogger.error(logFormat(context, line))
          case LogLevel.Fatal.level => slf4jLogger.error(logFormat(context, line))
        }
      )
    }
}
