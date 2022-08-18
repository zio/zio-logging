package zio.logging.backend

import zio.logging.backend.TestAppender.{ LogEntry, logLevelMapping }

import java.text.MessageFormat
import java.util.ResourceBundle
import java.util.concurrent.ConcurrentHashMap

class TestJPLogger(val name: String) extends System.Logger {
  override def getName: String = name

  override def isLoggable(level: System.Logger.Level): Boolean =
    level.getSeverity >= System.Logger.Level.INFO.getSeverity

  override def log(level: System.Logger.Level, bundle: ResourceBundle, msg: String, thrown: Throwable): Unit = {
    if (isLoggable(level)) {
      val entry = LogEntry(
        name,
        Thread.currentThread().getName,
        logLevelMapping(level),
        msg,
        System.currentTimeMillis(),
        Option(thrown).map(_.getMessage)
      )

      TestAppender.appendLogEntry(entry)
    }
    ()
  }

  override def log(level: System.Logger.Level, bundle: ResourceBundle, format: String, params: Any*): Unit = {
    if (isLoggable(level)) {
      val entry = LogEntry(
        name,
        Thread.currentThread().getName,
        logLevelMapping(level),
        MessageFormat.format(format, params),
        System.currentTimeMillis(),
        None
      )

      TestAppender.appendLogEntry(entry)
    }
    ()
  }

}

object TestJPLoggerSystem {
  private val loggers                        = new ConcurrentHashMap[String, TestJPLogger]()
  def getLogger(name: String): System.Logger =
    loggers.computeIfAbsent(name, n => new TestJPLogger(n))
}
