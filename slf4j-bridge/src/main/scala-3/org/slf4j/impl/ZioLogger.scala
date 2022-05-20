package org.slf4j.impl

import org.slf4j.helpers.MarkerIgnoringBase
import zio.{ Cause, ZIO }

class ZioLogger(name: String, factory: ZioLoggerFactory) extends MarkerIgnoringBase {
  private def run(f: ZIO[Any, Nothing, Unit]): Unit =
    factory.run {
      ZIO.logSpan(name)(f)
    }

  override def isTraceEnabled: Boolean = true

  override def trace(msg: String): Unit =
    run(ZIO.logTrace(msg))

  override def trace(format: String, arg: AnyRef): Unit =
    run(ZIO.logTrace(String.format(format, arg)))

  override def trace(format: String, arg1: AnyRef, arg2: AnyRef): Unit =
    run(ZIO.logTrace(String.format(format, arg1, arg2)))

  override def trace(format: String, arguments: Array[? <: Object]): Unit =
    run(ZIO.logTrace(String.format(format, arguments: _*)))

  override def trace(msg: String, t: Throwable): Unit =
    run(
      ZIO.logTraceCause(msg, Cause.die(t))
    )

  override def isDebugEnabled: Boolean = true

  override def debug(msg: String): Unit =
    run(ZIO.logDebug(msg))

  override def debug(format: String, arg: AnyRef): Unit =
    run(ZIO.logDebug(String.format(format, arg)))

  override def debug(format: String, arg1: AnyRef, arg2: AnyRef): Unit =
    run(ZIO.logDebug(String.format(format, arg1, arg2)))

  override def debug(format: String, arguments: Array[? <: Object]): Unit =
    run(ZIO.logDebug(String.format(format, arguments: _*)))

  override def debug(msg: String, t: Throwable): Unit =
    run(
      ZIO.logDebugCause(msg, Cause.die(t))
    )

  override def isInfoEnabled: Boolean = true

  override def info(msg: String): Unit =
    run(ZIO.logInfo(msg))

  override def info(format: String, arg: AnyRef): Unit =
    run(ZIO.logInfo(String.format(format, arg)))

  override def info(format: String, arg1: AnyRef, arg2: AnyRef): Unit =
    run(ZIO.logInfo(String.format(format, arg1, arg2)))

  override def info(format: String, arguments: Array[? <: Object]): Unit =
    run(ZIO.logInfo(String.format(format, arguments: _*)))

  override def info(msg: String, t: Throwable): Unit =
    run(
      ZIO.logInfoCause(msg, Cause.die(t))
    )

  override def isWarnEnabled: Boolean = true

  override def warn(msg: String): Unit =
    run(ZIO.logWarning(msg))

  override def warn(format: String, arg: AnyRef): Unit =
    run(ZIO.logWarning(String.format(format, arg)))

  override def warn(format: String, arg1: AnyRef, arg2: AnyRef): Unit =
    run(ZIO.logWarning(String.format(format, arg1, arg2)))

  override def warn(format: String, arguments: Array[? <: Object]): Unit =
    run(ZIO.logWarning(String.format(format, arguments: _*)))

  override def warn(msg: String, t: Throwable): Unit =
    run(
      ZIO.logWarningCause(msg, Cause.die(t))
    )

  override def isErrorEnabled: Boolean = true

  override def error(msg: String): Unit =
    run(ZIO.logError(msg))

  override def error(format: String, arg: AnyRef): Unit =
    run(ZIO.logError(String.format(format, arg)))

  override def error(format: String, arg1: AnyRef, arg2: AnyRef): Unit =
    run(ZIO.logError(String.format(format, arg1, arg2)))

  override def error(format: String, arguments: Array[? <: Object]): Unit =
    run(ZIO.logError(String.format(format, arguments: _*)))

  override def error(msg: String, t: Throwable): Unit =
    run(
      ZIO.logErrorCause(msg, Cause.die(t))
    )
}
