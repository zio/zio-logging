package org.slf4j.zio

import org.slf4j.event.Level
import org.slf4j.{ Marker, ZioLoggerFactory }
import org.slf4j.helpers.{ AbstractLogger, MessageFormatter }
import zio.{ Cause, LogLevel, ZIO }

class ZioLogger(name: String, factory: ZioLoggerFactory) extends AbstractLogger {
  override def getName: String = name

  override def getFullyQualifiedCallerName: String = null

  override def handleNormalizedLoggingCall(
    level: Level,
    marker: Marker,
    messagePattern: String,
    arguments: Array[Object],
    throwable: Throwable
  ): Unit =
    factory.run {
      val msg   = if (arguments != null) {
        MessageFormatter.arrayFormat(messagePattern, arguments).getMessage
      } else {
        messagePattern
      }
      val cause = if (throwable != null) {
        Cause.die(throwable)
      } else {
        Cause.empty
      }

      val logLevel = ZioLogger.logLevelMapping(level)

      ZIO.logSpan(name) {
        ZIO.logLevel(logLevel) {
          ZIO.logCause(msg, cause)
        }
      }
    }

  override def isTraceEnabled(marker: Marker): Boolean = true

  override def isDebugEnabled(marker: Marker): Boolean = true

  override def isInfoEnabled(marker: Marker): Boolean = true

  override def isWarnEnabled(marker: Marker): Boolean = true

  override def isErrorEnabled(marker: Marker): Boolean = true

  override def isTraceEnabled: Boolean = true

  override def isDebugEnabled: Boolean = true

  override def isInfoEnabled: Boolean = true

  override def isWarnEnabled: Boolean = true

  override def isErrorEnabled: Boolean = true
}

object ZioLogger {
  val logLevelMapping: Map[Level, LogLevel] = Map(
    Level.TRACE -> LogLevel.Trace,
    Level.DEBUG -> LogLevel.Debug,
    Level.INFO  -> LogLevel.Info,
    Level.WARN  -> LogLevel.Warning,
    Level.ERROR -> LogLevel.Error
  )
}
