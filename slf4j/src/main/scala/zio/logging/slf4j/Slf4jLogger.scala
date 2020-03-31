package zio.logging.slf4j

import org.slf4j.LoggerFactory
import zio.internal.Tracing
import zio.internal.stacktracer.Tracer
import zio.internal.stacktracer.ZTraceElement.{ NoLocation, SourceLocation }
import zio.internal.stacktracer.impl.AkkaLineNumbersTracer
import zio.internal.tracing.TracingConfig
import zio.logging.Logging.Logging
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

  def make(
    logFormat: (LogContext, => String) => String,
    rootLoggerName: Option[String] = None
  ): ZLayer[Any, Nothing, Logging] =
    Logging.make(
      logger = { (context, line) =>
        val loggerName = context.get(LogAnnotation.Name) match {
          case Nil   => classNameForLambda(line).getOrElse("ZIO.defaultLogger")
          case names => LogAnnotation.Name.render(names)
        }
        logger(loggerName).map {
          slf4jLogger =>
            val maybeThrowable = context.get(LogAnnotation.Throwable).orNull
            context.get(LogAnnotation.Level).level match {
              case LogLevel.Off.level   => ()
              case LogLevel.Debug.level => slf4jLogger.debug(logFormat(context, line), maybeThrowable)
              case LogLevel.Trace.level => slf4jLogger.trace(logFormat(context, line), maybeThrowable)
              case LogLevel.Info.level  => slf4jLogger.info(logFormat(context, line), maybeThrowable)
              case LogLevel.Warn.level  => slf4jLogger.warn(logFormat(context, line), maybeThrowable)
              case LogLevel.Error.level => slf4jLogger.error(logFormat(context, line), maybeThrowable)
              case LogLevel.Fatal.level => slf4jLogger.error(logFormat(context, line), maybeThrowable)
            }
        }
      },
      rootLoggerName = rootLoggerName
    )
}
