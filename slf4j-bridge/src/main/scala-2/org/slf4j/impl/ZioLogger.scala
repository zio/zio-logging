package org.slf4j.impl

import org.slf4j.helpers.{MarkerIgnoringBase, MessageFormatter}
import zio.ZIO
import zio.logging.{LogAnnotation, Logging, log}

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
    run(log.trace(MessageFormatter.format(format, arg).getMessage))

  override def trace(format: String, arg1: AnyRef, arg2: AnyRef): Unit =
    run(log.trace(MessageFormatter.format(format, arg1, arg2).getMessage))

  override def trace(format: String, arguments: AnyRef*): Unit =
    run(log.trace(MessageFormatter.format(format, arguments.toArray).getMessage))

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
    run(log.debug(MessageFormatter.format(format, arg).getMessage))

  override def debug(format: String, arg1: AnyRef, arg2: AnyRef): Unit =
    run(log.debug(MessageFormatter.format(format, arg1, arg2).getMessage))

  override def debug(format: String, arguments: AnyRef*): Unit =
    run(log.debug(MessageFormatter.format(format, arguments.toArray).getMessage))

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
    run(log.info(MessageFormatter.format(format, arg).getMessage))

  override def info(format: String, arg1: AnyRef, arg2: AnyRef): Unit =
    run(log.info(MessageFormatter.format(format, arg1, arg2).getMessage))

  override def info(format: String, arguments: AnyRef*): Unit =
    run(log.info(MessageFormatter.format(format, arguments.toArray).getMessage))

  override def info(msg: String, t: Throwable): Unit =
    run(
      log.locally(LogAnnotation.Throwable(Some(t))) {
        log.info(msg)
      }
    )

  override def isWarnEnabled: Boolean = true

  override def warn(msg: String): Unit =
    run(log.warn(msg))

  override def warn(format: String, arg: AnyRef): Unit = {
    run(log.warn(MessageFormatter.format(format, arg).getMessage))
  }

  override def warn(format: String, arg1: AnyRef, arg2: AnyRef): Unit =
    run(log.warn(MessageFormatter.format(format, arg1, arg2).getMessage))

  override def warn(format: String, arguments: AnyRef*): Unit =
    run(log.warn(MessageFormatter.format(format, arguments.toArray).getMessage))

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
    run(log.error(MessageFormatter.format(format, arg).getMessage))

  override def error(format: String, arg1: AnyRef, arg2: AnyRef): Unit =
    run(log.error(MessageFormatter.format(format, arg1, arg2).getMessage))

  override def error(format: String, arguments: AnyRef*): Unit =
    run(log.error(MessageFormatter.format(format, arguments.toArray).getMessage))

  override def error(msg: String, t: Throwable): Unit =
    run(
      log.locally(LogAnnotation.Throwable(Some(t))) {
        log.error(msg)
      }
    )
}
