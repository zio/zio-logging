package zio.logging.backend

import zio.{ Chunk, LogLevel }

import java.util.concurrent.atomic.AtomicReference
import scala.annotation.tailrec

object TestAppender {

  val logLevelMapping: Map[System.Logger.Level, LogLevel] = Map(
    System.Logger.Level.ALL     -> LogLevel.All,
    System.Logger.Level.TRACE   -> LogLevel.Trace,
    System.Logger.Level.DEBUG   -> LogLevel.Debug,
    System.Logger.Level.INFO    -> LogLevel.Info,
    System.Logger.Level.WARNING -> LogLevel.Warning,
    System.Logger.Level.ERROR   -> LogLevel.Error,
    System.Logger.Level.OFF     -> LogLevel.None
  )

  final case class LogEntry(
    loggerName: String,
    threadName: String,
    logLevel: LogLevel,
    message: String,
    timestamp: Long,
    cause: Option[Throwable]
  )

  private val logEntriesRef: AtomicReference[Chunk[LogEntry]] = new AtomicReference[Chunk[LogEntry]](Chunk.empty)

  private[backend] def appendLogEntry(event: LogEntry): Unit = {

    @tailrec
    def append(entry: LogEntry): Unit = {
      val old = logEntriesRef.get()
      if (logEntriesRef.compareAndSet(old, old :+ entry)) ()
      else append(entry)
    }

    append(event)
  }

  def reset(): Unit = logEntriesRef.set(Chunk.empty)

  def logOutput: Chunk[LogEntry] = logEntriesRef.get()

}
