package zio.logging.slf4j

import org.slf4j.LoggerFactory
import zio.internal.Tracing
import zio.internal.stacktracer.Tracer
import zio.internal.stacktracer.ZTraceElement.{NoLocation, SourceLocation}
import zio.internal.stacktracer.impl.AkkaLineNumbersTracer
import zio.internal.tracing.TracingConfig
import zio.logging._
import zio.{UIO, ZIO}

object Slf4jLogger {
  val tracing = Tracing(Tracer.globallyCached(new AkkaLineNumbersTracer), TracingConfig.enabled)

  private def logger(lambda: => AnyRef) = tracing.tracer.traceLocation(() => lambda) match {
    case SourceLocation(_, clazz, _, _) => ZIO.effectTotal(LoggerFactory.getLogger(clazz))
    case NoLocation(_)                  => ZIO.effectTotal(LoggerFactory.getLogger("ZIO.defaultLogger"))
  }

  def make(logFormat: (LogContext, => String) => String): UIO[Logger] = {
    Logger.make((context, line) =>
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
