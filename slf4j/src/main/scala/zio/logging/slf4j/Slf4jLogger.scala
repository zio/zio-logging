package zio.logging.slf4j

import org.slf4j.{ LoggerFactory, MDC }
import zio.internal.Tracing
import zio.internal.stacktracer.Tracer
import zio.internal.stacktracer.ZTraceElement.{ NoLocation, SourceLocation }
import zio.internal.stacktracer.impl.AkkaLineNumbersTracer
import zio.internal.tracing.TracingConfig
import zio.logging.{ Logging, _ }
import zio.{ ULayer, ZIO }

import scala.jdk.CollectionConverters._
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
    initialContext: LogContext = LogContext.empty
  ): ULayer[Logging] =
    LogAppender.make[Any, String](
      LogFormat.fromFunction(logFormat),
      (context, line) => {
        val loggerName = context.get(LogAnnotation.Name) match {
          case Nil   => classNameForLambda(line).getOrElse("ZIO.defaultLogger")
          case names => LogAnnotation.Name.render(names)
        }
        logger(loggerName).map { slf4jLogger =>
          val maybeThrowable = context.get(LogAnnotation.Throwable).orNull
          context.get(LogAnnotation.Level).level match {
            case LogLevel.Off.level   => ()
            case LogLevel.Debug.level => slf4jLogger.debug(line, maybeThrowable)
            case LogLevel.Trace.level => slf4jLogger.trace(line, maybeThrowable)
            case LogLevel.Info.level  => slf4jLogger.info(line, maybeThrowable)
            case LogLevel.Warn.level  => slf4jLogger.warn(line, maybeThrowable)
            case LogLevel.Error.level => slf4jLogger.error(line, maybeThrowable)
            case LogLevel.Fatal.level => slf4jLogger.error(line, maybeThrowable)
          }
        }
      }
    ) >>>
      Logging.make(
        initialContext
      )

  /**
   * Creates a slf4j logger that puts all the annotations defined in `mdcAnnotations` in the MDC context
   */
  def makeWithAnnotationsAsMdc(
    mdcAnnotations: List[LogAnnotation[_]],
    logFormat: (LogContext, => String) => String = (_, s) => s,
    initialContext: LogContext = LogContext.empty
  ): ULayer[Logging] = {
    val annotationNames = mdcAnnotations.map(_.name)

    LogAppender.make[Any, String](
      LogFormat.fromFunction(logFormat),
      (context, line) => {
        val loggerName = context.get(LogAnnotation.Name) match {
          case Nil   => classNameForLambda(line).getOrElse("ZIO.defaultLogger")
          case names => LogAnnotation.Name.render(names)
        }
        logger(loggerName).map { slf4jLogger =>
          val maybeThrowable = context.get(LogAnnotation.Throwable).orNull

          val mdc: Map[String, String] = context.renderContext.filter {
            case (k, _) => annotationNames.contains(k)
          }
          MDC.setContextMap(mdc.asJava)
          context.get(LogAnnotation.Level).level match {
            case LogLevel.Off.level   => ()
            case LogLevel.Debug.level => slf4jLogger.debug(line, maybeThrowable)
            case LogLevel.Trace.level => slf4jLogger.trace(line, maybeThrowable)
            case LogLevel.Info.level  => slf4jLogger.info(line, maybeThrowable)
            case LogLevel.Warn.level  => slf4jLogger.warn(line, maybeThrowable)
            case LogLevel.Error.level => slf4jLogger.error(line, maybeThrowable)
            case LogLevel.Fatal.level => slf4jLogger.error(line, maybeThrowable)
          }
          MDC.clear()
        }
      }
    ) >>> Logging.make(
      initialContext
    )
  }
}
