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

import zio.logging.internal.LogAppender
import zio.logging.{ LogFormat, LoggerNameExtractor }
import zio.{ Cause, FiberFailure, FiberId, FiberRefs, LogLevel, LogSpan, Runtime, Trace, ZLayer, ZLogger, logging }

/**
 * A logging backend that sends each ZIO log line to a `java.lang.System.Logger`, the Java Platform Logging API. With
 * no `System.LoggerFinder` installed, the JDK sends these lines to `java.util.logging`. `System.Logger` has no MDC or
 * key-value pairs, so the annotations of a line are written as `key=value` text in the message. Annotation keys and
 * values are appended to the message text as they are, so a value that contains a line break splits the message over
 * several lines.
 *
 * The backend applies no ZIO `LogFilter` of its own: it calls `System.Logger#isLoggable` with the mapped level, so the
 * `System.Logger` implementation decides which lines are written. `Fatal` is passed as `ERROR`, `All` as `ALL`, and
 * `LogLevel.None` as `OFF`; the JDK's default `java.util.logging` setup writes `OFF` lines.
 *
 * @see [[https://zio.dev/zio-logging/jpl/ Java Platform/System Logger]]
 */
object JPL {

  private[backend] val logLevelMapping: Map[LogLevel, System.Logger.Level] = Map(
    LogLevel.All     -> System.Logger.Level.ALL,
    LogLevel.Trace   -> System.Logger.Level.TRACE,
    LogLevel.Debug   -> System.Logger.Level.DEBUG,
    LogLevel.Info    -> System.Logger.Level.INFO,
    LogLevel.Warning -> System.Logger.Level.WARNING,
    LogLevel.Error   -> System.Logger.Level.ERROR,
    LogLevel.Fatal   -> System.Logger.Level.ERROR,
    LogLevel.None    -> System.Logger.Level.OFF
  )

  /**
   * The format the backend uses unless one is given: every annotation except the logger name, followed by the message
   * (`LogFormat.line`) and the cause (`LogFormat.cause`). The backend writes everything except the cause into the
   * message text, and passes the cause as the `Throwable` of the call.
   */
  val logFormatDefault: LogFormat =
    LogFormat.allAnnotations(excludeKeys = Set(logging.loggerNameAnnotationKey)) + LogFormat.line + LogFormat.cause

  private[backend] def getLoggerName(default: String = "zio-jpl-logger"): Trace => String =
    trace => LoggerNameExtractor.trace(trace, FiberRefs.empty, Map.empty).getOrElse(default)

  private def logAppender(systemLogger: System.Logger, logLevel: LogLevel): LogAppender = new LogAppender {
    self =>
    val message: StringBuilder = new StringBuilder()
    var throwable: Throwable   = null

    /**
     * Stores the `Throwable` of a non-empty cause: its first `Throwable` failure, else its first defect, else a
     * `FiberFailure` that wraps the whole cause.
     */
    override def appendCause(cause: Cause[Any]): Unit = {
      if (!cause.isEmpty) {
        val maybeThrowable = (cause.failures.collect { case t: Throwable => t } ++ cause.defects).headOption
        maybeThrowable match {
          case Some(t) => throwable = t
          case None    => throwable = FiberFailure(cause)
        }
      }
      ()
    }

    override def appendNumeric[A](numeric: A): Unit = appendText(numeric.toString)

    override def appendText(text: String): Unit = {
      message.append(text)
      ()
    }

    override def closeKeyOpenValue(): Unit =
      appendText("=")

    override def closeLogEntry(): Unit = {
      logLevelMapping.get(logLevel).foreach { level =>
        systemLogger.log(level, message.toString, throwable)
      }
      ()
    }

    override def closeValue(): Unit = appendText(" ")

    override def openKey(): Unit = ()

    override def openLogEntry(): Unit = {
      message.clear()

      throwable = null
      ()
    }
  }

  private def isLogLevelEnabled(systemLogger: System.Logger, logLevel: LogLevel): Boolean =
    logLevelMapping.get(logLevel).exists(systemLogger.isLoggable)

  /**
   * Returns a layer that adds a JPL logger, built by `jplLogger`, to the loggers of the ZIO runtime. Combine it with
   * `Runtime.removeDefaultLoggers` to replace the default console logger.
   *
   * {{{
   * import zio._
   * import zio.logging.{ LogFormat, LoggerNameExtractor }
   * import zio.logging.backend.JPL
   *
   * val loggerNameOrService: Trace => String =
   *   trace => LoggerNameExtractor.trace(trace, FiberRefs.empty, Map.empty).getOrElse("billing-service")
   *
   * val billingLogger: ZLayer[Any, Nothing, Unit] =
   *   Runtime.removeDefaultLoggers >>> JPL.jpl(
   *     LogFormat.label("fiber", LogFormat.fiberId) + JPL.logFormatDefault,
   *     loggerNameOrService
   *   )
   * }}}
   *
   * @param format how each line is split into the message text and the cause
   * @param loggerName the `System.Logger` name of a line that has no `logger_name` annotation
   */
  def jpl(
    format: LogFormat,
    loggerName: Trace => String
  ): ZLayer[Any, Nothing, Unit] =
    Runtime.addLogger(jplLogger(format, loggerName))

  /**
   * Returns the JPL backend layer with the given format. A line that has no `logger_name` annotation takes its logger
   * name from the `Trace` of the log call, or `"zio-jpl-logger"` when the trace cannot be parsed.
   */
  def jpl(
    format: LogFormat
  ): ZLayer[Any, Nothing, Unit] =
    jpl(format, getLoggerName())

  /**
   * The JPL backend layer with [[logFormatDefault]]. A line that has no `logger_name` annotation takes its logger name
   * from the `Trace` of the log call, or `"zio-jpl-logger"` when the trace cannot be parsed.
   *
   * {{{
   * import zio._
   * import zio.logging.backend.JPL
   *
   * object OrderApp extends ZIOAppDefault {
   *   override val bootstrap: ZLayer[ZIOAppArgs, Any, Any] =
   *     Runtime.removeDefaultLoggers >>> JPL.jpl
   *
   *   override def run: ZIO[Any, Any, Unit] =
   *     ZIO.logInfo("Order received") @@ ZIOAspect.annotated("order", "1042")
   * }
   * }}}
   *
   * @see [[https://zio.dev/zio-logging/jpl/ Java Platform/System Logger]]
   */
  val jpl: ZLayer[Any, Nothing, Unit] =
    jpl(logFormatDefault)

  /**
   * Returns the JPL backend as a `ZLogger`, without installing it, so that it can be wrapped, for example with a
   * `LogFilter`, before `Runtime.addLogger` installs it. Each line goes to `System.getLogger` of its logger name.
   *
   * {{{
   * import zio._
   * import zio.logging.{ LogFilter, ZLoggerOps }
   * import zio.logging.backend.JPL
   *
   * val infoAndAbove: ZLayer[Any, Nothing, Unit] =
   *   Runtime.removeDefaultLoggers >>> Runtime.addLogger(
   *     JPL.jplLogger(JPL.logFormatDefault).filter(LogFilter.logLevel(LogLevel.Info))
   *   )
   * }}}
   *
   * @param loggerName the `System.Logger` name of a line that has no `logger_name` annotation; by default the part of
   *   the trace location before the method name, or `"zio-jpl-logger"` when the trace cannot be parsed
   */
  def jplLogger(
    format: LogFormat,
    loggerName: Trace => String = getLoggerName()
  ): ZLogger[String, Unit] =
    jplLogger(format, loggerName, System.getLogger)

  private[backend] def jplLogger(
    format: LogFormat,
    loggerName: Trace => String,
    getJPLogger: String => System.Logger
  ): ZLogger[String, Unit] =
    JplLogger(format, loggerName, getJPLogger)

  private[logging] case class JplLogger(
    format: LogFormat,
    loggerName: Trace => String,
    getJPLogger: String => System.Logger
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
      val jpLoggerName = annotations.getOrElse(zio.logging.loggerNameAnnotationKey, loggerName(trace))
      val jpLogger     = getJPLogger(jpLoggerName)
      if (isLogLevelEnabled(jpLogger, logLevel)) {
        val appender = logAppender(jpLogger, logLevel)

        format.unsafeFormat(appender)(trace, fiberId, logLevel, message, cause, context, spans, annotations)
        appender.closeLogEntry()
      }
      ()
    }
  }

}
