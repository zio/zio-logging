package zio.logging.internal

import java.io.{ BufferedWriter, FileOutputStream, OutputStreamWriter, Writer }
import java.nio.charset.Charset
import java.nio.file.{ FileSystems, Path }
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

private[logging] sealed trait WriterProvider {
  def writer: Writer
}

private[logging] object WriterProvider {
  final case class SimpleWriterProvider(
    destination: Path,
    charset: Charset,
    bufferedIOSize: Option[Int]
  ) extends WriterProvider {
    override val writer: Writer = {
      val output = new OutputStreamWriter(new FileOutputStream(destination.toFile, true), charset)
      bufferedIOSize match {
        case Some(bufferSize) => new BufferedWriter(output, bufferSize)
        case None             => output
      }
    }
  }

  final case class TimeBasedRollingWriterProvider(
    destination: Path,
    charset: Charset,
    bufferedIOSize: Option[Int],
    time: () => LocalDateTime = TimeBasedRollingWriterProvider.makeNewTime
  ) extends WriterProvider {
    import java.util.concurrent.locks.ReentrantLock
    import TimeBasedRollingWriterProvider._

    private var timeInUse             = time()
    private var currentWriter: Writer = makeWriter(makePath(destination, timeInUse), charset, bufferedIOSize)
    private val lock: ReentrantLock   = new ReentrantLock()

    override def writer: Writer = {
      val newTime = time()
      if (newTime != timeInUse) {
        lock.lock()
        try
          if (newTime != timeInUse) {
            currentWriter.close()
            currentWriter = makeWriter(makePath(destination, newTime), charset, bufferedIOSize)
            timeInUse = newTime
          }
        finally
          lock.unlock()
      }
      currentWriter
    }
  }
  object TimeBasedRollingWriterProvider {
    private val dateTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    private def makeNewTime()                        = LocalDateTime.now().truncatedTo(ChronoUnit.DAYS)

    def makePath(destination: Path, time: LocalDateTime): Path = {
      val formattedTime   = dateTimeFormatter.format(time)
      val fileNameArray   = destination.getFileName.toString.split("\\.")
      val timeFileName    = if (fileNameArray.length >= 2) {
        fileNameArray.dropRight(1).mkString(".") + "-" + formattedTime + "." + fileNameArray.last
      } else {
        fileNameArray.head + "-" + formattedTime
      }
      val timeDestination = FileSystems.getDefault.getPath(destination.getParent.toString, timeFileName)
      timeDestination
    }

    private def makeWriter(path: Path, charset: Charset, bufferedIOSize: Option[Int]): Writer = {
      val output = new OutputStreamWriter(new FileOutputStream(path.toFile, true), charset)
      bufferedIOSize match {
        case Some(bufferSize) => new BufferedWriter(output, bufferSize)
        case None             => output
      }
    }
  }
}
