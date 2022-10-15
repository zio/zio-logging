package org.slf4j.impl

import org.slf4j.helpers.{ MessageFormatter, ZioLoggerBase }
import zio.{ Cause, ZIO }

class ZioLogger(name: String, factory: ZioLoggerFactory) extends ZioLoggerBase(name) {

  private def run(f: ZIO[Any, Nothing, Unit]): Unit =
    factory.run {
      ZIO.logSpan(name)(f)
    }

  override def isTraceEnabled: Boolean = true

  override def trace(msg: String): Unit =
    run(ZIO.logTrace(msg))

  override def trace(format: String, arg: AnyRef): Unit =
    run(ZIO.logTrace(MessageFormatter.format(format, arg).getMessage))

  override def trace(format: String, arg1: AnyRef, arg2: AnyRef): Unit =
    run(ZIO.logTrace(MessageFormatter.format(format, arg1, arg2).getMessage))

  override def trace(format: String, arguments: Array[? <: Object]): Unit =
    run(ZIO.logTrace(MessageFormatter.arrayFormat(format, arguments.asInstanceOf[Array[Object]]).getMessage))

  override def trace(msg: String, t: Throwable): Unit =
    run(
      ZIO.logTraceCause(msg, Option(t).map(t => Cause.die(t)).getOrElse(Cause.empty))
    )

  override def isDebugEnabled: Boolean = true

  override def debug(msg: String): Unit =
    run(ZIO.logDebug(msg))

  override def debug(format: String, arg: AnyRef): Unit =
    run(ZIO.logDebug(MessageFormatter.format(format, arg).getMessage))

  override def debug(format: String, arg1: AnyRef, arg2: AnyRef): Unit =
    run(ZIO.logDebug(MessageFormatter.format(format, arg1, arg2).getMessage))

  override def debug(format: String, arguments: Array[? <: Object]): Unit =
    run(ZIO.logDebug(MessageFormatter.arrayFormat(format, arguments.asInstanceOf[Array[Object]]).getMessage))

  override def debug(msg: String, t: Throwable): Unit =
    run(
      ZIO.logDebugCause(msg, Option(t).map(t => Cause.die(t)).getOrElse(Cause.empty))
    )

  override def isInfoEnabled: Boolean = true

  override def info(msg: String): Unit =
    run(ZIO.logInfo(msg))

  override def info(format: String, arg: AnyRef): Unit =
    run(ZIO.logInfo(MessageFormatter.format(format, arg).getMessage))

  override def info(format: String, arg1: AnyRef, arg2: AnyRef): Unit =
    run(ZIO.logInfo(MessageFormatter.format(format, arg1, arg2).getMessage))

  override def info(format: String, arguments: Array[? <: Object]): Unit =
    run(ZIO.logInfo(MessageFormatter.arrayFormat(format, arguments.asInstanceOf[Array[Object]]).getMessage))

  override def info(msg: String, t: Throwable): Unit =
    run(
      ZIO.logInfoCause(msg, Option(t).map(t => Cause.die(t)).getOrElse(Cause.empty))
    )

  override def isWarnEnabled: Boolean = true

  override def warn(msg: String): Unit =
    run(ZIO.logWarning(msg))

  override def warn(format: String, arg: AnyRef): Unit =
    run(ZIO.logWarning(MessageFormatter.format(format, arg).getMessage))

  override def warn(format: String, arg1: AnyRef, arg2: AnyRef): Unit =
    run(ZIO.logWarning(MessageFormatter.format(format, arg1, arg2).getMessage))

  override def warn(format: String, arguments: Array[? <: Object]): Unit =
    run(ZIO.logWarning(MessageFormatter.arrayFormat(format, arguments.asInstanceOf[Array[Object]]).getMessage))

  override def warn(msg: String, t: Throwable): Unit =
    run(
      ZIO.logWarningCause(msg, Option(t).map(t => Cause.die(t)).getOrElse(Cause.empty))
    )

  override def isErrorEnabled: Boolean = true

  override def error(msg: String): Unit =
    run(ZIO.logError(msg))

  override def error(format: String, arg: AnyRef): Unit =
    run(ZIO.logError(MessageFormatter.format(format, arg).getMessage))

  override def error(format: String, arg1: AnyRef, arg2: AnyRef): Unit =
    run(ZIO.logError(MessageFormatter.format(format, arg1, arg2).getMessage))

  override def error(format: String, arguments: Array[? <: Object]): Unit =
    run(ZIO.logError(MessageFormatter.arrayFormat(format, arguments.asInstanceOf[Array[Object]]).getMessage))

  override def error(msg: String, t: Throwable): Unit =
    run(
      ZIO.logErrorCause(msg, Option(t).map(t => Cause.die(t)).getOrElse(Cause.empty))
    )
}
