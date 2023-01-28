package zio.logging.backend

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.AppenderBase
import zio.{ Chunk, LogLevel }

import java.util.concurrent.atomic.AtomicReference
import scala.annotation.tailrec
import scala.jdk.CollectionConverters._

class TestAppender extends AppenderBase[ILoggingEvent] {
  override def append(event: ILoggingEvent): Unit =
    TestAppender.appendLogEntry(event)
}

object TestAppender {

  val logLevelMapping: Map[Level, LogLevel] = Map(
    Level.ALL   -> LogLevel.All,
    Level.TRACE -> LogLevel.Trace,
    Level.DEBUG -> LogLevel.Debug,
    Level.INFO  -> LogLevel.Info,
    Level.WARN  -> LogLevel.Warning,
    Level.ERROR -> LogLevel.Error,
    Level.OFF   -> LogLevel.None
  )

  final case class LogEntry(
    loggerName: String,
    threadName: String,
    logLevel: LogLevel,
    message: String,
    timestamp: Long,
    cause: Option[String],
    keyValues: Map[String, String]
  )

  object LogEntry {
    def apply(event: ILoggingEvent): LogEntry = {
      val keyValues = if (event.getKeyValuePairs != null) {
        event.getKeyValuePairs.asScala.map(kv => (kv.key, kv.value.toString)).toMap
      } else Map.empty[String, String]
      val cause     = Option(event.getThrowableProxy).map(_.getMessage)
      val level     = logLevelMapping(event.getLevel)

      LogEntry(
        event.getLoggerName,
        event.getThreadName,
        level,
        event.getMessage,
        event.getTimeStamp,
        cause,
        keyValues
      )
    }
  }

  private val logEntriesRef: AtomicReference[Chunk[LogEntry]] = new AtomicReference[Chunk[LogEntry]](Chunk.empty)

  private def appendLogEntry(event: ILoggingEvent): Unit = {

    @tailrec
    def append(entry: LogEntry): Unit = {
      val old = logEntriesRef.get()
      if (logEntriesRef.compareAndSet(old, old :+ entry)) ()
      else append(entry)
    }

    append(LogEntry(event))
  }

  def reset(): Unit = logEntriesRef.set(Chunk.empty)

  def logOutput: Chunk[LogEntry] = logEntriesRef.get()

}
