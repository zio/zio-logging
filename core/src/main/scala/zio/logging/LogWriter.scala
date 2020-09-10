package zio.logging

import java.io.{ BufferedWriter, FileWriter, Writer }
import java.nio.charset.Charset
import java.nio.file.Path

private[logging] class LogWriter(
  destination: Path,
  charset: Charset,
  autoFlushBatchSize: Int,
  bufferedIOSize: Option[Int]
) extends Writer {
  private val writer: Writer = bufferedIOSize match {
    case Some(bufferSize) => new BufferedWriter(new FileWriter(destination.toFile, charset), bufferSize)
    case None             => new FileWriter(destination.toFile, charset)
  }

  private var entriesWritten: Long = 0

  def write(buffer: Array[Char], offset: Int, length: Int): Unit =
    writer.write(buffer, offset, length)

  def writeln(line: String): Unit = {
    writer.write(line)
    writer.write(System.lineSeparator)

    entriesWritten += 1

    if (entriesWritten % autoFlushBatchSize == 0)
      writer.flush()
  }

  def flush(): Unit = writer.flush()

  def close(): Unit = writer.close()
}
