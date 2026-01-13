/*
 * Copyright 2019-2026 John A. De Goes and the ZIO Contributors
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

import java.io.Writer
import java.nio.charset.Charset
import java.nio.file.Path

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

}
