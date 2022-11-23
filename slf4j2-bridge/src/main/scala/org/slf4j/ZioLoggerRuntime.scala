package org.slf4j

import org.slf4j.event.Level
import org.slf4j.helpers.MessageFormatter

final class ZioLoggerRuntime(runtime: _root_.zio.Runtime[Any]) extends org.slf4j.zio.LoggerRuntime {

  override def log(
    name: String,
    level: Level,
    marker: Marker,
    messagePattern: String,
    arguments: Array[AnyRef],
    throwable: Throwable
  ): Unit =
    _root_.zio.Unsafe.unsafe { implicit u =>
      runtime.unsafe.run {
        val logLevel = ZioLoggerRuntime.logLevelMapping(level)
        _root_.zio.ZIO.logSpan(name) {
          _root_.zio.ZIO.logLevel(logLevel) {
            lazy val msg = if (arguments != null) {
              MessageFormatter.arrayFormat(messagePattern, arguments.toArray).getMessage
            } else {
              messagePattern
            }
            val cause    = if (throwable != null) {
              _root_.zio.Cause.die(throwable)
            } else {
              _root_.zio.Cause.empty
            }

            _root_.zio.ZIO.logCause(msg, cause)
          }
        }
      }
      ()
    }
}
object ZioLoggerRuntime {

  val logLevelMapping: Map[Level, _root_.zio.LogLevel] = Map(
    Level.TRACE -> _root_.zio.LogLevel.Trace,
    Level.DEBUG -> _root_.zio.LogLevel.Debug,
    Level.INFO  -> _root_.zio.LogLevel.Info,
    Level.WARN  -> _root_.zio.LogLevel.Warning,
    Level.ERROR -> _root_.zio.LogLevel.Error
  )

  def initialize(runtime: _root_.zio.Runtime[Any]): Unit =
    LoggerFactory
      .getProvider()
      .getLoggerFactory()
      .asInstanceOf[org.slf4j.zio.LoggerFactory]
      .attacheRuntime(new ZioLoggerRuntime(runtime))
}
