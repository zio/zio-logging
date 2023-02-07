/*
 * Copyright 2019-2023 John A. De Goes and the ZIO Contributors
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

import org.slf4j.event.Level
import org.slf4j.{ Logger, LoggerFactory, Marker, MarkerFactory }
import zio.logging.internal.LogAppender
import zio.logging.{ LogFormat, LoggerNameExtractor }
import zio.{ Cause, FiberFailure, FiberId, FiberRefs, LogLevel, LogSpan, Runtime, Trace, ZIOAspect, ZLayer, ZLogger }

object SLF4J {

  /**
   * log aspect annotation key for slf4j marker name
   */
  val logMarkerNameAnnotationKey = "slf4j_log_marker_name"

  /**
   * default log format for slf4j logger
   */
  val logFormatDefault: LogFormat =
    LogFormat.allAnnotations(excludeKeys =
      Set(zio.logging.loggerNameAnnotationKey, logMarkerNameAnnotationKey)
    ) + LogFormat.line + LogFormat.cause

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
    trace => LoggerNameExtractor.trace(trace, FiberRefs.empty, Map.empty).getOrElse(default)

  private val logLevelMapping: Map[LogLevel, Level] = Map(
    LogLevel.All     -> Level.TRACE,
    LogLevel.Trace   -> Level.TRACE,
    LogLevel.Debug   -> Level.DEBUG,
    LogLevel.Info    -> Level.INFO,
    LogLevel.Warning -> Level.WARN,
    LogLevel.Error   -> Level.ERROR,
    LogLevel.Fatal   -> Level.ERROR
  )

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
      val message              = new StringBuilder()
      val keyValues            = new scala.collection.mutable.ArrayBuffer[(String, String)]()
      var throwable: Throwable = null

      /**
       * cause as throwable
       */
      override def appendCause(cause: Cause[Any]): Unit = {
        if (!cause.isEmpty) {
          cause match {
            case Cause.Die(t, _)             => throwable = t
            case Cause.Fail(t: Throwable, _) => throwable = t
            case _                           => throwable = FiberFailure(cause)
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

      /**
       * all key-value into slf4j key-values
       */
      override def appendKeyValue(key: String, value: String): Unit = {
        keyValues.append((key, value))
        ()
      }

      /**
       * all key-value into slf4j key-values
       */
      override def appendKeyValue(key: String, appendValue: LogAppender => Unit): Unit = {
        val builder = new StringBuilder()
        appendValue(LogAppender.unstructured(builder.append(_)))
        keyValues.append((key, builder.toString()))
        ()
      }

      override def closeLogEntry(): Unit = {
        logLevelMapping.get(logLevel).foreach { level =>
          var builder = slf4jLogger.atLevel(level).setMessage(message.toString).setCause(throwable)

          slf4jMarker.foreach { m =>
            builder = builder.addMarker(m)
          }

          builder = keyValues.foldLeft(builder) { case (b, (k, v)) =>
            b.addKeyValue(k, v)
          }

          builder.log()
        }
        ()
      }

      override def closeValue(): Unit = ()

      override def openKey(): Unit = ()

      override def openLogEntry(): Unit = {
        message.clear()
        keyValues.clear()
        throwable = null
        ()
      }
    }

  /**
   * Use this layer to register an use an Slf4j logger in your app.
   * To avoid double logging, you should create this layer only once in your application
   */
  def slf4j(
    format: LogFormat,
    loggerName: Trace => String
  ): ZLayer[Any, Nothing, Unit] =
    Runtime.addLogger(slf4jLogger(format, loggerName))

  /**
   * Use this layer to register an use an Slf4j logger in your app.
   * To avoid double logging, you should create this layer only once in your application
   */
  def slf4j(
    format: LogFormat
  ): ZLayer[Any, Nothing, Unit] =
    slf4j(format, getLoggerName())

  /**
   * Use this layer to register an use an Slf4j logger in your app.
   * To avoid double logging, you should create this layer only once in your application
   */
  def slf4j: ZLayer[Any, Nothing, Unit] =
    slf4j(logFormatDefault)

  def slf4jLogger(
    format: LogFormat,
    loggerName: Trace => String
  ): ZLogger[String, Unit] = {
    // get some slf4j logger to invoke slf4j initialisation
    // as in some program failure cases it may happen, that program exit sooner then log message will be logged (#616)
    LoggerFactory.getLogger("zio-slf4j-logger")

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
        val slf4jLoggerName = annotations.getOrElse(zio.logging.loggerNameAnnotationKey, loggerName(trace))
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

}
