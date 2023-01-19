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
package org.slf4j.impl

import org.slf4j.helpers.{ MessageFormatter, ZioLoggerBase }
import zio.logging.slf4j.bridge.Slf4jBridge
import zio.{ Cause, ZIO }

class ZioLogger(name: String, factory: ZioLoggerFactory) extends ZioLoggerBase(name) {

  private def run(f: ZIO[Any, Nothing, Unit]): Unit =
    factory.run {
      ZIO.logSpan(name)(ZIO.logAnnotate(Slf4jBridge.loggerNameAnnotationKey, name)(f))
    }

  override def isTraceEnabled: Boolean = true

  override def trace(msg: String): Unit =
    run(ZIO.logTrace(msg))

  override def trace(format: String, arg: AnyRef): Unit =
    run(ZIO.logTrace(MessageFormatter.format(format, arg).getMessage))

  override def trace(format: String, arg1: AnyRef, arg2: AnyRef): Unit =
    run(ZIO.logTrace(MessageFormatter.format(format, arg1, arg2).getMessage))

  override def trace(format: String, arguments: AnyRef*): Unit =
    run(ZIO.logTrace(MessageFormatter.arrayFormat(format, arguments.toArray).getMessage))

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

  override def debug(format: String, arguments: AnyRef*): Unit =
    run(ZIO.logDebug(MessageFormatter.arrayFormat(format, arguments.toArray).getMessage))

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

  override def info(format: String, arguments: AnyRef*): Unit =
    run(ZIO.logInfo(MessageFormatter.arrayFormat(format, arguments.toArray).getMessage))

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

  override def warn(format: String, arguments: AnyRef*): Unit =
    run(ZIO.logWarning(MessageFormatter.arrayFormat(format, arguments.toArray).getMessage))

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

  override def error(format: String, arguments: AnyRef*): Unit =
    run(ZIO.logError(MessageFormatter.arrayFormat(format, arguments.toArray).getMessage))

  override def error(msg: String, t: Throwable): Unit =
    run(
      ZIO.logErrorCause(msg, Option(t).map(t => Cause.die(t)).getOrElse(Cause.empty))
    )
}
