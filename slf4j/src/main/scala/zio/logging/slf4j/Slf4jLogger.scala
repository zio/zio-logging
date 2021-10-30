package zio.logging.slf4j

import org.slf4j.{ LoggerFactory, MDC }
import zio.logging.LogAppender._
import zio.logging.{ Logging, _ }
import zio.{ ULayer, ZIO }

import scala.jdk.CollectionConverters._

object Slf4jLogger {

  private def logger(name: String) =
    ZIO.succeed(
      LoggerFactory.getLogger(
        name
      )
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
          (context.get(LogAnnotation.Level).level: @unchecked) match {
            case LogLevel.Off.level   => ()
            case LogLevel.Debug.level => if (slf4jLogger.isDebugEnabled) slf4jLogger.debug(line, maybeThrowable)
            case LogLevel.Trace.level => if (slf4jLogger.isTraceEnabled) slf4jLogger.trace(line, maybeThrowable)
            case LogLevel.Info.level  => if (slf4jLogger.isInfoEnabled) slf4jLogger.info(line, maybeThrowable)
            case LogLevel.Warn.level  => if (slf4jLogger.isWarnEnabled) slf4jLogger.warn(line, maybeThrowable)
            case LogLevel.Error.level => if (slf4jLogger.isErrorEnabled) slf4jLogger.error(line, maybeThrowable)
            case LogLevel.Fatal.level => if (slf4jLogger.isErrorEnabled) slf4jLogger.error(line, maybeThrowable)
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
    val annotationNames = mdcAnnotations.map(_.name).toSet
    val filter          = (renderContext: Map[String, String]) =>
      renderContext.filter { case (k, _) =>
        annotationNames.contains(k)
      }
    makeWithAnnotationsAsMdcWithFilter(filter, logFormat)
  }

  /**
   * Creates a slf4j logger that puts all the annotations in the MDC context unless excludes by the names that are
   * defined in `mdcAnnotations` in the MDC context
   */
  def makeWithAllAnnotationsAsMdc(
    excludeMdcAnnotations: Set[String] = Set.empty,
    logFormat: (LogContext, => String) => String = (_, s) => s
  ): ULayer[Logging] = {
    val filter = (renderContext: Map[String, String]) =>
      renderContext.filterNot { case (k, _) =>
        excludeMdcAnnotations.contains(k)
      }
    makeWithAnnotationsAsMdcWithFilter(filter, logFormat)
  }

  def makeWithAnnotationsAsMdcWithFilter(
    filter: Map[String, String] => Map[String, String],
    logFormat: (LogContext, => String) => String = (_, s) => s
  ): ULayer[Logging] =
    LogAppender.make[Any, String](
      LogFormat.fromFunction(logFormat),
      (context, line) => {
        val loggerName = context(LogAnnotation.Name)
        logger(loggerName).map { slf4jLogger =>
          val maybeThrowable = context.get(LogAnnotation.Throwable).orNull

          val mdc: Map[String, String] = filter(context.renderContext)
          val previous                 = Option(MDC.getCopyOfContextMap()).getOrElse(Map.empty[String, String].asJava)
          MDC.setContextMap(mdc.asJava)
          try {
            (context.get(LogAnnotation.Level).level: @unchecked) match {
              case LogLevel.Off.level   => ()
              case LogLevel.Debug.level => slf4jLogger.debug(line, maybeThrowable)
              case LogLevel.Trace.level => slf4jLogger.trace(line, maybeThrowable)
              case LogLevel.Info.level  => slf4jLogger.info(line, maybeThrowable)
              case LogLevel.Warn.level  => slf4jLogger.warn(line, maybeThrowable)
              case LogLevel.Error.level => slf4jLogger.error(line, maybeThrowable)
              case LogLevel.Fatal.level => slf4jLogger.error(line, maybeThrowable)
            }
          } finally MDC.setContextMap(previous)
        }
      }
    ) >>> withLoggerNameFromLine[String] >>> Logging.make
}
