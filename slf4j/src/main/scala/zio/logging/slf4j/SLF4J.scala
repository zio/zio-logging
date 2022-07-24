package zio.logging.backend

import org.slf4j.{ Logger, LoggerFactory, MDC }
import zio.logging.LogFormat
import zio.logging.internal.LogAppender
import zio.{ Cause, FiberFailure, FiberId, FiberRefs, LogLevel, LogSpan, Runtime, Trace, ZLayer, ZLogger }

import java.util
import scala.collection.mutable

object SLF4J {

  val logFormatDefault: LogFormat = LogFormat.allAnnotations + LogFormat.line + LogFormat.cause

  def getLoggerName(default: String = "zio-slf4j-logger"): Trace => String =
    _ match {
      case Trace(location, _, _) =>
        val last = location.lastIndexOf(".")
        if (last > 0) {
          location.substring(0, last)
        } else location

      case _ => default
    }

  def isLogLevelEnabled(slf4jLogger: Logger, logLevel: LogLevel): Boolean =
    logLevel match {
      case LogLevel.All     => slf4jLogger.isTraceEnabled
      case LogLevel.Trace   => slf4jLogger.isTraceEnabled
      case LogLevel.Debug   => slf4jLogger.isDebugEnabled
      case LogLevel.Info    => slf4jLogger.isInfoEnabled
      case LogLevel.Warning => slf4jLogger.isWarnEnabled
      case LogLevel.Error   => slf4jLogger.isErrorEnabled
      case LogLevel.Fatal   => slf4jLogger.isErrorEnabled
      case _                => false
    }

  //    val toThrowableDefault: Any => Throwable = _ match {
  //      case t: Throwable => t
  //      case e => new Exception(e.toString)
  //    }
  //
  //    def getThrowable(cause: Cause[Any], toThrowable: Any => Throwable = toThrowableDefault): Option[Throwable] = {
  //      if(!cause.isEmpty) {
  //        Some(cause.squashWith(toThrowable))
  //      } else None
  //    }

  def getThrowable(cause: Cause[Any]): Option[Throwable] =
    if (!cause.isEmpty) {
      Some(FiberFailure(cause))
    } else None

  def logAppender(slf4jLogger: Logger, logLevel: LogLevel): LogAppender = new LogAppender { self =>
    val enabled = isLogLevelEnabled(slf4jLogger, logLevel)

    val message: mutable.StringBuilder         = new mutable.StringBuilder()
    val mdc: java.util.HashMap[String, String] = new util.HashMap[String, String]()
    var throwable: Throwable                   = null

    /**
     * cause as throwable
     */
    override def appendCause(cause: Cause[Any]): Unit = {
      if (enabled)
        getThrowable(cause).foreach { t =>
          throwable = t
        }
      ()
    }

    override def appendNumeric[A](numeric: A): Unit = appendText(numeric.toString)

    override def appendText(text: String): Unit = {
      if (enabled) message.append(text)
      ()
    }

    override def closeKeyOpenValue(): Unit = ()

    /**
     * all key-value into mdc
     */
    override def appendKeyValue(key: String, value: String): Unit = {
      if (enabled) mdc.put(key, value)
      ()
    }

    override def closeLogEntry(): Unit = {
      if (enabled) {
        val previous =
          if (!mdc.isEmpty) {
            val previous =
              Some(Option(MDC.getCopyOfContextMap).getOrElse(java.util.Collections.emptyMap[String, String]()))
            MDC.setContextMap(mdc)
            previous
          } else None

        try logLevel match {
          case LogLevel.All     => slf4jLogger.trace(message.toString, throwable)
          case LogLevel.Trace   => slf4jLogger.trace(message.toString, throwable)
          case LogLevel.Debug   => slf4jLogger.debug(message.toString, throwable)
          case LogLevel.Info    => slf4jLogger.info(message.toString, throwable)
          case LogLevel.Warning => slf4jLogger.warn(message.toString, throwable)
          case LogLevel.Error   => slf4jLogger.error(message.toString, throwable)
          case LogLevel.Fatal   => slf4jLogger.error(message.toString, throwable)
          case LogLevel.None    => ()
          case _                => ()
        } finally previous.foreach(MDC.setContextMap)
      }
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

  def slf4j(
    logLevel: zio.LogLevel,
    format: LogFormat,
    rootLoggerName: Trace => String
  ): ZLayer[Any, Nothing, Unit] =
    Runtime.addLogger(slf4jLogger(rootLoggerName, logLevel, format))

  def slf4j(
    logLevel: zio.LogLevel,
    format: LogFormat
  ): ZLayer[Any, Nothing, Unit] =
    slf4j(logLevel, format, getLoggerName())

  def slf4j(
    logLevel: zio.LogLevel
  ): ZLayer[Any, Nothing, Unit] =
    slf4j(logLevel, logFormatDefault, getLoggerName())

  private def slf4jLogger(
    rootLoggerName: Trace => String,
    rootLogLevel: LogLevel,
    format: LogFormat
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
        if (logLevel >= rootLogLevel) {
          val slf4jLogger = LoggerFactory.getLogger(rootLoggerName(trace))
          val appender    = logAppender(slf4jLogger, logLevel)

          format.unsafeFormat(appender)(trace, fiberId, logLevel, message, cause, context, spans, annotations)
          appender.closeLogEntry()
        }
        ()
      }
    }

}
