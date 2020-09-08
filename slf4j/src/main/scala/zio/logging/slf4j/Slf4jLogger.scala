package zio.logging.slf4j

import zio.Tag
import org.slf4j.{ LoggerFactory, MDC }
import zio.internal.Tracing
import zio.internal.stacktracer.Tracer
import zio.internal.stacktracer.ZTraceElement.{ NoLocation, SourceLocation }
import zio.internal.stacktracer.impl.AkkaLineNumbersTracer
import zio.internal.tracing.TracingConfig
import zio.logging.LogAppender.Service
import zio.logging.{ Logging, _ }
import zio.{ UIO, ULayer, ZIO, ZLayer }

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

  private def withLoggerNameFromLine[A <: AnyRef: Tag]: ZLayer[Appender[A], Nothing, Appender[A]] =
    ZLayer.fromFunction((appender: Appender[A]) =>
      new Service[A] {

        override def write(ctx: LogContext, msg: => A): UIO[Unit] = {
          val ctxWithName = ctx.get(LogAnnotation.Name) match {
            case Nil =>
              ctx.annotate(
                LogAnnotation.Name,
                classNameForLambda(msg).getOrElse("ZIO.defaultLogger") :: Nil
              )
            case _   => ctx
          }
          appender.get.write(ctxWithName, msg)
        }
      }
    )

  def make(
    logFormat: (LogContext, => String) => String
  ): ULayer[Logging] =
    LogAppender.make[Any, String](
      LogFormat.fromFunction(logFormat),
      (context, line) => {
        val loggerName = context(LogAnnotation.Name)
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
    ) >>> withLoggerNameFromLine[String] >>>
      Logging.make

  /**
   * Creates a slf4j logger that puts all the annotations defined in `mdcAnnotations` in the MDC context
   */
  def makeWithAnnotationsAsMdc(
    mdcAnnotations: List[LogAnnotation[_]],
    logFormat: (LogContext, => String) => String = (_, s) => s
  ): ULayer[Logging] = {
    val annotationNames = mdcAnnotations.map(_.name)

    LogAppender.make[Any, String](
      LogFormat.fromFunction(logFormat),
      (context, line) => {
        val loggerName = context(LogAnnotation.Name)
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
    ) >>> withLoggerNameFromLine[String] >>> Logging.make
  }
}
