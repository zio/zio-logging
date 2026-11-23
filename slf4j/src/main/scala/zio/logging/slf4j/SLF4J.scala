/*
 * Copyright 2019-2026 John A. De Goes and the ZIO Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package zio.logging.backend

import org.slf4j.{ Logger, LoggerFactory, MDC, Marker, MarkerFactory }
import zio.logging.internal.LogAppender
import zio.logging.{ LogFormat, LoggerNameExtractor }
import zio.{
  Cause,
  FiberFailure,
  FiberId,
  FiberRefs,
  LogLevel,
  LogSpan,
  Runtime,
  Trace,
  ZIOAspect,
  ZLayer,
  ZLogger,
  logging
}

import java.util

/**
 * A logging backend that sends each ZIO log line to an SLF4J logger, so that the SLF4J provider on the classpath,
 * such as Logback, writes it. The `zio-logging-slf4j` module builds against `slf4j-api` 1.7.x and puts the
 * annotations of a line into the MDC; the `zio-logging-slf4j2` module builds against `slf4j-api` 2.x and passes them
 * as SLF4J key-value pairs. Both modules define this object with the same public members.
 *
 * The backend applies no ZIO `LogFilter` of its own: it asks the SLF4J logger whether the level is enabled, so the
 * provider's configuration decides which lines are written. `Fatal` is written as `ERROR`, `All` as `TRACE`, and
 * lines at `LogLevel.None` are not written.
 *
 * @see [[https://zio.dev/zio-logging/slf4j1/ SLF4J v1]]
 * @see [[https://zio.dev/zio-logging/slf4j2/ SLF4J v2]]
 */
object SLF4J {

  /**
   * The default conversion from the cause of a log line to the `Throwable` passed to SLF4J. It returns `None` for an
   * empty cause, and otherwise a [[zio.FiberFailure]] that wraps the whole cause, also when the cause holds only an
   * interruption or a failure that is not a `Throwable`.
   */
  val causeToThrowableDefault: Cause[Any] => Option[Throwable] = cause =>
    if (cause.isEmpty) {
      None
    } else {
      Some(FiberFailure(cause))
    }

  /**
   * The annotation key, `"slf4j_log_marker_name"`, that holds the SLF4J marker name of a log line. [[logMarkerName]]
   * sets it; the backend turns its value into a marker with `MarkerFactory.getMarker`, and [[logFormatDefault]] leaves
   * it out of the annotations.
   */
  val logMarkerNameAnnotationKey = "slf4j_log_marker_name"

  /**
   * The format the backend uses unless one is given: every annotation except the logger name and the marker name,
   * followed by the message (`LogFormat.line`) and the cause (`LogFormat.cause`). The backend turns the annotations
   * into MDC entries (`zio-logging-slf4j`) or key-value pairs (`zio-logging-slf4j2`), the text into the SLF4J
   * message, and the cause into the `Throwable` of the call.
   */
  val logFormatDefault: LogFormat =
    LogFormat.allAnnotations(excludeKeys =
      Set(SLF4J.logMarkerNameAnnotationKey, logging.loggerNameAnnotationKey)
    ) + LogFormat.line + LogFormat.cause

  /**
   * Returns an aspect that sets the SLF4J marker of every log line in the effect it wraps to the marker named `value`,
   * by adding the annotation [[logMarkerNameAnnotationKey]]. Provider filters can then match the marker, such as
   * Logback's `MarkerFilter`.
   *
   * {{{
   * import zio._
   * import zio.logging.backend.SLF4J
   *
   * val declined: UIO[Unit] = ZIO.logWarning("Card declined") @@ SLF4J.logMarkerName("PAYMENT")
   * }}}
   */
  def logMarkerName(value: String): ZIOAspect[Nothing, Any, Nothing, Any, Nothing, Any] =
    ZIOAspect.annotated(logMarkerNameAnnotationKey, value)

  /**
   * Returns a function that takes the SLF4J logger name from a `Trace`: the part of the trace location before the
   * method name. For example, the trace `example.LivePingService.ping(PingService.scala:22)` gives
   * `example.LivePingService`. A trace that cannot be parsed, such as `Trace.empty`, gives `default`.
   */
  def getLoggerName(default: String = "zio-slf4j-logger"): Trace => String =
    trace => LoggerNameExtractor.trace(trace, FiberRefs.empty, Map.empty).getOrElse(default)

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

  private def logAppender(
    slf4jLogger: Logger,
    slf4jMarker: Option[Marker],
    logLevel: LogLevel,
    causeToThrowable: Cause[Any] => Option[Throwable]
  ): LogAppender =
    new LogAppender { self =>
      val message: StringBuilder                 = new StringBuilder()
      val mdc: java.util.HashMap[String, String] = new util.HashMap[String, String]()
      var throwable: Throwable                   = null

      /**
       * Stores the cause, converted with `causeToThrowable`, as the `Throwable` of the SLF4J call.
       */
      override def appendCause(cause: Cause[Any]): Unit = {
        throwable = causeToThrowable(cause).orNull
        ()
      }

      override def appendNumeric[A](numeric: A): Unit = appendText(numeric.toString)

      override def appendText(text: String): Unit = {
        message.append(text)
        ()
      }

      override def closeKeyOpenValue(): Unit =
        appendText("=")

      /**
       * Puts the key-value pair into the MDC that is set while this line is logged.
       */
      override def appendKeyValue(key: String, value: String): Unit = {
        mdc.put(key, value)
        ()
      }

      /**
       * Puts the key-value pair into the MDC that is set while this line is logged.
       */
      override def appendKeyValue(key: String, appendValue: LogAppender => Unit): Unit = {
        val builder = new StringBuilder()
        appendValue(LogAppender.unstructured(builder.append(_)))
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

        try
          logLevel match {
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
          }
        finally previous.foreach(MDC.setContextMap)

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

  /**
   * Returns a layer that adds an SLF4J logger, built by [[slf4jLogger]], to the loggers of the ZIO runtime. Combine it
   * with `Runtime.removeDefaultLoggers` to replace the default console logger. Each built SLF4J layer adds its own
   * logger, so build only one in an application: with two, every line is logged twice.
   *
   * {{{
   * import zio._
   * import zio.logging.LogFormat
   * import zio.logging.backend.SLF4J
   *
   * val billingLogger: ZLayer[Any, Nothing, Unit] =
   *   Runtime.removeDefaultLoggers >>> SLF4J.slf4j(
   *     SLF4J.logFormatDefault + LogFormat.spans,
   *     SLF4J.getLoggerName("billing-service"),
   *     SLF4J.causeToThrowableDefault
   *   )
   * }}}
   *
   * @param format how each line is split into the message, the annotations and the cause
   * @param loggerName the SLF4J logger name of a line that has no `logger_name` annotation
   * @param causeToThrowable the conversion from the cause of a line to the `Throwable` passed to SLF4J
   */
  def slf4j(
    format: LogFormat,
    loggerName: Trace => String,
    causeToThrowable: Cause[Any] => Option[Throwable]
  ): ZLayer[Any, Nothing, Unit] =
    Runtime.addLogger(slf4jLogger(format, loggerName, causeToThrowable))

  /**
   * Returns the SLF4J backend layer with the given format, the logger name from `getLoggerName()` and
   * [[causeToThrowableDefault]]. Build only one SLF4J layer in an application: with two, every line is logged twice.
   */
  def slf4j(
    format: LogFormat
  ): ZLayer[Any, Nothing, Unit] =
    slf4j(format, getLoggerName(), causeToThrowableDefault)

  /**
   * The SLF4J backend layer with [[logFormatDefault]], the logger name from `getLoggerName()` and
   * [[causeToThrowableDefault]]. Build only one SLF4J layer in an application: with two, every line is logged twice.
   *
   * {{{
   * import zio._
   * import zio.logging.backend.SLF4J
   *
   * object OrderApp extends ZIOAppDefault {
   *   override val bootstrap: ZLayer[ZIOAppArgs, Any, Any] =
   *     Runtime.removeDefaultLoggers >>> SLF4J.slf4j
   *
   *   override def run: ZIO[Any, Any, Unit] =
   *     ZIO.logInfo("Order received") @@ ZIOAspect.annotated("order", "1042")
   * }
   * }}}
   *
   * @see [[https://zio.dev/zio-logging/slf4j1/ SLF4J v1]]
   * @see [[https://zio.dev/zio-logging/slf4j2/ SLF4J v2]]
   */
  def slf4j: ZLayer[Any, Nothing, Unit] =
    slf4j(logFormatDefault)

  /**
   * Returns the SLF4J backend as a `ZLogger`, without installing it, so that it can be wrapped, for example with a
   * `LogFilter`, before `Runtime.addLogger` installs it. It also looks up one SLF4J logger at once, which makes SLF4J
   * initialize its provider now rather than at the first log line.
   *
   * {{{
   * import zio._
   * import zio.logging.{ LogFilter, ZLoggerOps }
   * import zio.logging.backend.SLF4J
   *
   * val slf4jLogger: ZLogger[String, Unit] =
   *   SLF4J.slf4jLogger(SLF4J.logFormatDefault, SLF4J.getLoggerName(), SLF4J.causeToThrowableDefault)
   *
   * val infoAndAbove: ZLayer[Any, Nothing, Unit] =
   *   Runtime.removeDefaultLoggers >>> Runtime.addLogger(slf4jLogger.filter(LogFilter.logLevel(LogLevel.Info)))
   * }}}
   */
  def slf4jLogger(
    format: LogFormat,
    loggerName: Trace => String,
    causeToThrowable: Cause[Any] => Option[Throwable]
  ): ZLogger[String, Unit] = {
    // get some slf4j logger to invoke slf4j initialisation
    // as in some program failure cases it may happen, that program exit sooner then log message will be logged (#616)
    LoggerFactory.getLogger("zio-slf4j-logger")

    Slf4jLogger(format, loggerName, causeToThrowable)
  }

  private[logging] case class Slf4jLogger(
    format: LogFormat,
    loggerName: Trace => String,
    causeToThrowable: Cause[Any] => Option[Throwable]
  ) extends ZLogger[String, Unit] {

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
      val slf4jLoggerName = annotations.getOrElse(logging.loggerNameAnnotationKey, loggerName(trace))
      val slf4jLogger     = LoggerFactory.getLogger(slf4jLoggerName)
      val slf4jMarkerName = annotations.get(SLF4J.logMarkerNameAnnotationKey)
      val slf4jMarker     = slf4jMarkerName.map(n => MarkerFactory.getMarker(n))
      if (isLogLevelEnabled(slf4jLogger, slf4jMarker, logLevel)) {
        val appender = logAppender(slf4jLogger, slf4jMarker, logLevel, causeToThrowable)

        format.unsafeFormat(appender)(trace, fiberId, logLevel, message, cause, context, spans, annotations)
        appender.closeLogEntry()
      }
      ()
    }
  }

}
