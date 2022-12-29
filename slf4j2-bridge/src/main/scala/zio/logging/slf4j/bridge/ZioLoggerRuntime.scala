package zio.logging.slf4j.bridge

import org.slf4j.Marker
import org.slf4j.event.Level
import org.slf4j.helpers.MessageFormatter
import zio.{ Cause, LogLevel, Runtime, Unsafe, ZIO }

final class ZioLoggerRuntime(runtime: Runtime[Any]) extends LoggerRuntime {

  override def log(
    name: String,
    level: Level,
    marker: Marker,
    messagePattern: String,
    arguments: Array[AnyRef],
    throwable: Throwable
  ): Unit =
    Unsafe.unsafe { implicit u =>
      runtime.unsafe.run {
        val logLevel = ZioLoggerRuntime.logLevelMapping(level)
        ZIO.logSpan(name) {
          ZIO.logAnnotate(Slf4jBridge.loggerNameAnnotationKey, name) {
            ZIO.logLevel(logLevel) {
              lazy val msg = if (arguments != null) {
                MessageFormatter.arrayFormat(messagePattern, arguments.toArray).getMessage
              } else {
                messagePattern
              }
              val cause    = if (throwable != null) {
                Cause.die(throwable)
              } else {
                Cause.empty
              }

              ZIO.logCause(msg, cause)
            }
          }
        }
      }
      ()
    }
}

object ZioLoggerRuntime {

  val logLevelMapping: Map[Level, LogLevel] = Map(
    Level.TRACE -> LogLevel.Trace,
    Level.DEBUG -> LogLevel.Debug,
    Level.INFO  -> LogLevel.Info,
    Level.WARN  -> LogLevel.Warning,
    Level.ERROR -> LogLevel.Error
  )
}
