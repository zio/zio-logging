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

import java.io.{ BufferedWriter, File, FileOutputStream, OutputStreamWriter, Writer }
import java.nio.charset.Charset
import java.nio.file.{ FileSystems, Path }
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private[logging] class FileWriter(
  destination: Path,
  charset: Charset,
  autoFlushBatchSize: Int,
  bufferedIOSize: Option[Int],
  rolling: Option[FileLoggerConfig.FileRollingPolicy]
) extends Writer {
  private var currentDestination             = destination
  private def makeWriter(path: Path): Writer = {
    val output = new OutputStreamWriter(new FileOutputStream(path.toFile, true), charset)
    bufferedIOSize match {
      case Some(bufferSize) => new BufferedWriter(output, bufferSize)
      case None             => output
    }
  }
  private var writer: Writer                 = rolling match {
    case Some(policy) =>
      policy match {
        case FileRollingPolicy.TimeBasedRollingPolicy =>
          val newPath = makeDatePath()
          currentDestination = newPath
          makeWriter(newPath)
      }
    case None         => makeWriter(destination)
  }

  private var entriesWritten: Long = 0

  final def write(buffer: Array[Char], offset: Int, length: Int): Unit =
    writer.write(buffer, offset, length)

  private def makeDatePath(): Path = {
    val currentDateTime = LocalDateTime.now()
    val formatter       = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    val time            = formatter.format(currentDateTime)
    val fileNameArray   = destination.getFileName.toString.split("\\.")
    val timeFileName    = if (fileNameArray.length >= 2) {
      fileNameArray.dropRight(1).mkString(".") + "-" + time + "." + fileNameArray.last
    } else {
      fileNameArray.head + "-" + time
    }
    val timeDestination = FileSystems.getDefault.getPath(destination.getParent.toString, timeFileName)
    timeDestination
  }

  final def writeln(line: String): Unit = {
    rolling match {
      case Some(policy) =>
        policy match {
          case FileRollingPolicy.TimeBasedRollingPolicy =>
            val newPath = makeDatePath()
            if (newPath != currentDestination) {
              println("새로운 파일 생성")
              currentDestination = newPath
              writer.close()
              writer = makeWriter(newPath)
            }
        }
      case None         => ()
    }
    writer.write(line)
    writer.write(System.lineSeparator)

    entriesWritten += 1

    if (entriesWritten % autoFlushBatchSize == 0)
      writer.flush()
  }

  final def flush(): Unit = writer.flush()

  final def close(): Unit = writer.close()
}
