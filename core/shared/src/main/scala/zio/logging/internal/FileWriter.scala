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
package zio.logging.internal

import zio.logging.FileLoggerConfig
import zio.logging.FileLoggerConfig.FileRollingPolicy

import java.io.{BufferedWriter, FileOutputStream, OutputStreamWriter, Writer}
import java.nio.charset.Charset
import java.nio.file.{FileSystems, Path}
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

private[logging] class FileWriter(
  destination: Path,
  charset: Charset,
  autoFlushBatchSize: Int,
  bufferedIOSize: Option[Int],
  rollingPolicy: Option[FileLoggerConfig.FileRollingPolicy]
) extends Writer {
  private val writerProvider: WriterProvider = rollingPolicy match {
    case Some(policy) =>
      policy match {
        case FileRollingPolicy.TimeBasedRollingPolicy =>
          WriterProvider.TimeBasedRollingWriterProvider(destination, charset, bufferedIOSize)
      }
    case None         => WriterProvider.SimpleWriterProvider(destination, charset, bufferedIOSize)
  }

  private var entriesWritten: Long = 0

  final def write(buffer: Array[Char], offset: Int, length: Int): Unit =
    writerProvider.writer.write(buffer, offset, length)

  final def writeln(line: String): Unit = {
    val writer = writerProvider.writer
    writer.write(line)
    writer.write(System.lineSeparator)

    entriesWritten += 1

    if (entriesWritten % autoFlushBatchSize == 0)
      writer.flush()
  }

  final def flush(): Unit = writerProvider.writer.flush()

  final def close(): Unit = writerProvider.writer.close()

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
      private var timeInUse = makeNewTime
      private var currentWriter: Writer = makeWriter(makePath(timeInUse))
      private def makeNewTime = LocalDateTime.now().truncatedTo(ChronoUnit.DAYS)
      private def makePath(time: LocalDateTime): Path = {
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        val formattedTime = formatter.format(time)
        val fileNameArray   = destination.getFileName.toString.split("\\.")
        val timeFileName    = if (fileNameArray.length >= 2) {
          fileNameArray.dropRight(1).mkString(".") + "-" + formattedTime + "." + fileNameArray.last
        } else {
          fileNameArray.head + "-" + formattedTime
        }
        val timeDestination = FileSystems.getDefault.getPath(destination.getParent.toString, timeFileName)
        timeDestination
      }

      private def makeWriter(path: Path): Writer = {
        val output = new OutputStreamWriter(new FileOutputStream(path.toFile, true), charset)
        bufferedIOSize match {
          case Some(bufferSize) => new BufferedWriter(output, bufferSize)
          case None             => output
        }
      }

      override def writer: Writer = {
        val newTime = makeNewTime
        if (newTime != timeInUse) {
          currentWriter.close()
          currentWriter = makeWriter(makePath(newTime))
          timeInUse = newTime
        }
        currentWriter
      }
    }
  }
}
