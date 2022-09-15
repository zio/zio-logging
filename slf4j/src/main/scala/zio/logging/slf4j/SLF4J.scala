package zio.logging.backend

import org.slf4j.{ Logger, LoggerFactory, MDC, Marker, MarkerFactory }
import zio.logging.LogFormat
import zio.logging.internal.LogAppender
import zio.{ Cause, FiberFailure, FiberId, FiberRefs, LogLevel, LogSpan, Runtime, Trace, ZIOAspect, ZLayer, ZLogger }

import java.util

object SLF4J {

  /**
   * log aspect annotation key for slf4j logger name
   */
  val loggerNameAnnotationKey = "slf4j_logger_name"

  /**
   * log aspect annotation key for slf4j marker name
   */
  val logMarkerNameAnnotationKey = "slf4j_log_marker_name"

  /**
   * default log format for slf4j logger
   */
  val logFormatDefault: LogFormat =
    LogFormat.allAnnotations(excludeKeys =
      Set(loggerNameAnnotationKey, logMarkerNameAnnotationKey)
    ) + LogFormat.line + LogFormat.cause

  /**
   * slf4j logger name aspect, by this aspect is possible to change default logger name (default logger name is extracted from [[Trace]])
   *
   * annotation key: [[SLF4J.loggerNameAnnotationKey]]
   */
  def loggerName(value: String): ZIOAspect[Nothing, Any, Nothing, Any, Nothing, Any] =
    ZIOAspect.annotated(loggerNameAnnotationKey, value)

  /**
   * slf4j marker name aspect
   *
   * annotation key: [[SLF4J.logMarkerNameAnnotationKey]]
   */
  def logMarkerName(value: String): ZIOAspect[Nothing, Any, Nothing, Any, Nothing, Any] =
    ZIOAspect.annotated(logMarkerNameAnnotationKey, value)

  /**
   * get logger name from [[Trace]]
   *
   * trace with value ''example.LivePingService.ping(PingService.scala:22)''
   * will have ''example.LivePingService'' as logger name
   */
  def getLoggerName(default: String = "zio-slf4j-logger"): Trace => String =
    zio.logging.getLoggerName(default)

  private def isLogLevelEnabled(slf4jLogger: Logger, slf4jMarker: Option[Marker], logLevel: LogLevel): Boolean =
    logLevel match {
      case LogLevel.All     => slf4jMarker.fold(slf4jLogger.isTraceEnabled)(m => slf4jLogger.isTraceEnabled(m))
      case LogLevel.Trace   => slf4jMarker.fold(slf4jLogger.isTraceEnabled)(m => slf4jLogger.isTraceEnabled(m))
      case LogLevel.Debug   => slf4jMarker.fold(slf4jLogger.isDebugEnabled)(m => slf4jLogger.isDebugEnabled(m))
      case LogLevel.Info    => slf4jMarker.fold(slf4jLogger.isInfoEnabled)(m => slf4jLogger.isInfoEnabled(m))
      case LogLevel.Warning => slf4jMarker.fold(slf4jLogger.isWarnEnabled)(m => slf4jLogger.isWarnEnabled(m))
      case LogLevel.Error   => slf4jMarker.fold(slf4jLogger.isErrorEnabled)(m => slf4jLogger.isErrorEnabled(m))
      case LogLevel.Fatal   => slf4jMarker.fold(slf4jLogger.isErrorEnabled)(m => slf4jLogger.isErrorEnabled(m))
      case _                => false
    }

  private def logAppender(slf4jLogger: Logger, slf4jMarker: Option[Marker], logLevel: LogLevel): LogAppender =
    new LogAppender { self =>
      val message: StringBuilder                 = new StringBuilder()
      val mdc: java.util.HashMap[String, String] = new util.HashMap[String, String]()
      var throwable: Throwable                   = null

      /**
       * cause as throwable
       */
      override def appendCause(cause: Cause[Any]): Unit = {
        if (!cause.isEmpty) {
          throwable = FiberFailure(cause)
        }
        ()
      }

      override def appendNumeric[A](numeric: A): Unit = appendText(numeric.toString)

      override def appendText(text: String): Unit = {
        message.append(text)
        ()
      }

      override def closeKeyOpenValue(): Unit = ()

      /**
       * all key-value into mdc
       */
      override def appendKeyValue(key: String, value: String): Unit = {
        mdc.put(key, value)
        ()
      }

      /**
       * all key-value into mdc
       */
      override def appendKeyValue(key: String, appendValue: LogAppender => Unit): Unit = {
        val builder = new StringBuilder()
        appendValue(LogAppender.unstructured(builder.append(_)))
        builder.toString()
        mdc.put(key, builder.toString())
        ()
      }

      override def closeLogEntry(): Unit = {
        val previous =
          if (!mdc.isEmpty) {
            val previous =
              Some(Option(MDC.getCopyOfContextMap).getOrElse(java.util.Collections.emptyMap[String, String]()))
            MDC.setContextMap(mdc)
            previous
          } else None

        try logLevel match {
          case LogLevel.All     =>
            slf4jMarker.fold(slf4jLogger.trace(message.toString, throwable))(m =>
              slf4jLogger.trace(m, message.toString, throwable)
            )
          case LogLevel.Trace   =>
            slf4jMarker.fold(slf4jLogger.trace(message.toString, throwable))(m =>
              slf4jLogger.trace(m, message.toString, throwable)
            )
          case LogLevel.Debug   =>
            slf4jMarker.fold(slf4jLogger.debug(message.toString, throwable))(m =>
              slf4jLogger.debug(m, message.toString, throwable)
            )
          case LogLevel.Info    =>
            slf4jMarker.fold(slf4jLogger.info(message.toString, throwable))(m =>
              slf4jLogger.info(m, message.toString, throwable)
            )
          case LogLevel.Warning =>
            slf4jMarker.fold(slf4jLogger.warn(message.toString, throwable))(m =>
              slf4jLogger.warn(m, message.toString, throwable)
            )
          case LogLevel.Error   =>
            slf4jMarker.fold(slf4jLogger.error(message.toString, throwable))(m =>
              slf4jLogger.error(m, message.toString, throwable)
            )
          case LogLevel.Fatal   =>
            slf4jMarker.fold(slf4jLogger.error(message.toString, throwable))(m =>
              slf4jLogger.error(m, message.toString, throwable)
            )
          case LogLevel.None    => ()
          case _                => ()
        } finally previous.foreach(MDC.setContextMap)

        ()
      }

      override def closeValue(): Unit = ()

      override def openKey(): Unit = ()

      override def openLogEntry(): Unit = {
        message.clear()
        mdc.clear()
        throwable = null
        ()
      }
    }

  @deprecated("use layer without logLevel", "2.0.1")
  def slf4j(
    logLevel: zio.LogLevel,
    format: LogFormat,
    loggerName: Trace => String
  ): ZLayer[Any, Nothing, Unit] =
    Runtime.addLogger(slf4jLogger(format, loggerName).filterLogLevel(_ >= logLevel))

  @deprecated("use layer without logLevel", "2.0.1")
  def slf4j(
    logLevel: zio.LogLevel,
    format: LogFormat
  ): ZLayer[Any, Nothing, Unit] =
    slf4j(logLevel, format, getLoggerName())

  @deprecated("use layer without logLevel", "2.0.1")
  def slf4j(
    logLevel: zio.LogLevel
  ): ZLayer[Any, Nothing, Unit] =
    slf4j(logLevel, logFormatDefault, getLoggerName())

  def slf4j(
    format: LogFormat,
    loggerName: Trace => String
  ): ZLayer[Any, Nothing, Unit] =
    Runtime.addLogger(slf4jLogger(format, loggerName))

  def slf4j(
    format: LogFormat
  ): ZLayer[Any, Nothing, Unit] =
    slf4j(format, getLoggerName())

  def slf4j: ZLayer[Any, Nothing, Unit] =
    slf4j(logFormatDefault)

  def slf4jLogger(
    format: LogFormat,
    loggerName: Trace => String
  ): ZLogger[String, Unit] =
    new ZLogger[String, Unit] {
      override def apply(
        trace: Trace,
        fiberId: FiberId,
        logLevel: LogLevel,
        message: () => String,
        cause: Cause[Any],
        context: FiberRefs,
        spans: List[LogSpan],
        annotations: Map[String, String]
      ): Unit = {
        val slf4jLoggerName = annotations.getOrElse(loggerNameAnnotationKey, loggerName(trace))
        val slf4jLogger     = LoggerFactory.getLogger(slf4jLoggerName)
        val slf4jMarkerName = annotations.get(logMarkerNameAnnotationKey)
        val slf4jMarker     = slf4jMarkerName.map(n => MarkerFactory.getMarker(n))
        if (isLogLevelEnabled(slf4jLogger, slf4jMarker, logLevel)) {
          val appender = logAppender(slf4jLogger, slf4jMarker, logLevel)

          format.unsafeFormat(appender)(trace, fiberId, logLevel, message, cause, context, spans, annotations)
          appender.closeLogEntry()
        }
        ()
      }
    }

}
