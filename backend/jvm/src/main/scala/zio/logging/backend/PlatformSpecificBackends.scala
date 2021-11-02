package zio.logging.backend

import org.slf4j.{ LoggerFactory, MDC }
import zio.logging.{ LogFormat, LogFormatType, logAnnotation }
import zio.{ FiberId, LogLevel, LogSpan, RuntimeConfigAspect, ZFiberRef, ZLogger, ZTraceElement }

import scala.jdk.CollectionConverters._

trait PlatformSpecificBackends {

  def slf4j(
    logLevel: zio.LogLevel,
    format: LogFormat[String],
    rootLoggerName: ZTraceElement => String
  ): RuntimeConfigAspect =
    RuntimeConfigAspect.addLogger(slf4jLogger(rootLoggerName, logLevel, format))

  def slf4j(
    logLevel: zio.LogLevel,
    format: LogFormat[String]
  ): RuntimeConfigAspect =
    slf4j(logLevel, format, _ => "zio-slf4j-logger")

  def slf4j(
    logLevel: zio.LogLevel
  ): RuntimeConfigAspect =
    slf4j(logLevel, LogFormat.default, _ => "zio-slf4j-logger")

  private def slf4jLogger(
    rootLoggerName: ZTraceElement => String,
    logLevel: LogLevel,
    format: LogFormat[String]
  ): ZLogger[Unit] =
    new ZLogger[Unit] {
      val formatLogger: ZLogger[Option[String]] =
        format.toLogger(LogFormatType.string).filterLogLevel(_ >= logLevel)

      override def apply(
        trace: ZTraceElement,
        fiberId: FiberId,
        logLevel: LogLevel,
        message: () => String,
        context: Map[ZFiberRef.Runtime[_], AnyRef],
        spans: List[LogSpan]
      ): Unit =
        formatLogger(trace, fiberId, logLevel, message, context, spans).foreach { message =>
          val slf4jLogger = LoggerFactory.getLogger(rootLoggerName(trace))

          val previous =
            context.get(logAnnotation) match {
              case Some(annotations: Map[String, String] @unchecked) if annotations.nonEmpty =>
                val previous =
                  Some(Option(MDC.getCopyOfContextMap).getOrElse(java.util.Collections.emptyMap[String, String]()))
                MDC.setContextMap(annotations.asJava)
                previous
              case _                                                                         =>
                None
            }

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
