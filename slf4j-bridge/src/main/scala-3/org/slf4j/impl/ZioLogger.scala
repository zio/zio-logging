package org.slf4j.impl

import org.slf4j.helpers.MarkerIgnoringBase
import zio.ZIO
import zio.logging.{ log, LogAnnotation, Logging }

class ZioLogger(name: String, factory: ZioLoggerFactory) extends MarkerIgnoringBase {
  private val nameList                                  = name.split('.').toList
  private def run(f: ZIO[Logging, Nothing, Unit]): Unit =
    factory.run {
      log.locally(LogAnnotation.Name(nameList))(f)
    }

  override def isTraceEnabled: Boolean = true

  override def trace(msg: String): Unit =
    run(log.trace(msg))

  override def trace(format: String, arg: AnyRef): Unit =
    run(log.trace(String.format(format, arg)))

  override def trace(format: String, arg1: AnyRef, arg2: AnyRef): Unit =
    run(log.trace(String.format(format, arg1, arg2)))

  override def trace(format: String, arguments: Array[? <: Object]): Unit =
    run(log.trace(String.format(format, arguments: _*)))

  override def trace(msg: String, t: Throwable): Unit =
    run(
      log.locally(LogAnnotation.Throwable(Some(t))) {
        log.trace(msg)
      }
    )

  override def isDebugEnabled: Boolean = true

  override def debug(msg: String): Unit =
    run(log.debug(msg))

  override def debug(format: String, arg: AnyRef): Unit =
    run(log.debug(String.format(format, arg)))

  override def debug(format: String, arg1: AnyRef, arg2: AnyRef): Unit =
    run(log.debug(String.format(format, arg1, arg2)))

  override def debug(format: String, arguments: Array[? <: Object]): Unit =
    run(log.debug(String.format(format, arguments: _*)))

  override def debug(msg: String, t: Throwable): Unit =
    run(
      log.locally(LogAnnotation.Throwable(Some(t))) {
        log.debug(msg)
      }
    )

  override def isInfoEnabled: Boolean = true

  override def info(msg: String): Unit =
    run(log.info(msg))

  override def info(format: String, arg: AnyRef): Unit =
    run(log.info(String.format(format, arg)))

  override def info(format: String, arg1: AnyRef, arg2: AnyRef): Unit =
    run(log.info(String.format(format, arg1, arg2)))

  override def info(format: String, arguments: Array[? <: Object]): Unit =
    run(log.info(String.format(format, arguments: _*)))

  override def info(msg: String, t: Throwable): Unit =
    run(
      log.locally(LogAnnotation.Throwable(Some(t))) {
        log.info(msg)
      }
    )

  override def isWarnEnabled: Boolean = true

  override def warn(msg: String): Unit =
    run(log.warn(msg))

  override def warn(format: String, arg: AnyRef): Unit =
    run(log.warn(String.format(format, arg)))

  override def warn(format: String, arg1: AnyRef, arg2: AnyRef): Unit =
    run(log.warn(String.format(format, arg1, arg2)))

  override def warn(format: String, arguments: Array[? <: Object]): Unit =
    run(log.warn(String.format(format, arguments: _*)))

  override def warn(msg: String, t: Throwable): Unit =
    run(
      log.locally(LogAnnotation.Throwable(Some(t))) {
        log.warn(msg)
      }
    )

  override def isErrorEnabled: Boolean = true

  override def error(msg: String): Unit =
    run(log.error(msg))

  override def error(format: String, arg: AnyRef): Unit =
    run(log.error(String.format(format, arg)))

  override def error(format: String, arg1: AnyRef, arg2: AnyRef): Unit =
    run(log.error(String.format(format, arg1, arg2)))

  override def error(format: String, arguments: Array[? <: Object]): Unit =
    run(log.error(String.format(format, arguments: _*)))

  override def error(msg: String, t: Throwable): Unit =
    run(
      log.locally(LogAnnotation.Throwable(Some(t))) {
        log.error(msg)
      }
    )
}
