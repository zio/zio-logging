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
    bufferedIOSize: Option[Int]
  ) extends WriterProvider {
    import TimeBasedRollingWriterProvider._

    private var timeInUse             = makeNewTime
    private var currentWriter: Writer = makeWriter(makePath(destination, timeInUse), charset, bufferedIOSize)
    val formatter: DateTimeFormatter  = DateTimeFormatter.ofPattern("yyyy-MM-dd")


    override def writer: Writer = {
      val newTime = makeNewTime
      if (newTime != timeInUse) {
        currentWriter.close()
        currentWriter = makeWriter(makePath(destination, timeInUse), charset, bufferedIOSize)
        timeInUse = newTime
      }
      currentWriter
    }
  }
  object TimeBasedRollingWriterProvider {
    private val dateTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    private def makeNewTime = LocalDateTime.now().truncatedTo(ChronoUnit.DAYS)

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
