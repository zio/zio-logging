package zio.logging.backend

import com.github.ghik.silencer.silent
import org.slf4j.{ LoggerFactory, MDC }
import zio.logging.LogFormat
import zio.{ Cause, FiberId, LogLevel, LogSpan, RuntimeConfigAspect, ZFiberRef, ZLogger, ZTraceElement }

import scala.collection.JavaConverters._

object SLF4J {

  def slf4j(
    logLevel: zio.LogLevel,
    format: LogFormat,
    rootLoggerName: ZTraceElement => String
  ): RuntimeConfigAspect =
    RuntimeConfigAspect.addLogger(slf4jLogger(rootLoggerName, logLevel, format))

  def slf4j(
    logLevel: zio.LogLevel,
    format: LogFormat
  ): RuntimeConfigAspect =
    slf4j(logLevel, format, _ => "zio-slf4j-logger")

  def slf4j(
    logLevel: zio.LogLevel
  ): RuntimeConfigAspect =
    slf4j(logLevel, LogFormat.default, _ => "zio-slf4j-logger")

  private def slf4jLogger(
    rootLoggerName: ZTraceElement => String,
    logLevel: LogLevel,
    format: LogFormat
  ): ZLogger[String, Unit] =
    new ZLogger[String, Unit] {
      val formatLogger: ZLogger[String, Option[String]] =
        format.toLogger.filterLogLevel(_ >= logLevel)

      override def apply(
        trace: ZTraceElement,
        fiberId: FiberId,
        logLevel: LogLevel,
        message: () => String,
        cause: Cause[Any],
        context: Map[ZFiberRef.Runtime[_], AnyRef],
        spans: List[LogSpan],
        annotations: Map[String, String]
      ): Unit =
        formatLogger(trace, fiberId, logLevel, message, cause, context, spans, annotations).foreach { message =>
          val slf4jLogger = LoggerFactory.getLogger(rootLoggerName(trace))

          val previous =
            if (annotations.nonEmpty) {
              val previous =
                Some(Option(MDC.getCopyOfContextMap).getOrElse(java.util.Collections.emptyMap[String, String]()))
              MDC.setContextMap(annotations.asJava: @silent("JavaConverters"))
              previous
            } else None

          try logLevel match {
            case LogLevel.All     => if (slf4jLogger.isTraceEnabled) slf4jLogger.trace(message)
            case LogLevel.Debug   => if (slf4jLogger.isDebugEnabled) slf4jLogger.debug(message)
            case LogLevel.Info    => if (slf4jLogger.isInfoEnabled) slf4jLogger.info(message)
            case LogLevel.Warning => if (slf4jLogger.isWarnEnabled) slf4jLogger.warn(message)
            case LogLevel.Error   => if (slf4jLogger.isErrorEnabled) slf4jLogger.error(message)
            case LogLevel.Fatal   => if (slf4jLogger.isErrorEnabled) slf4jLogger.error(message)
            case LogLevel.None    => ()
            case _                => ()
          } finally previous.foreach(MDC.setContextMap)
        }
    }
}
