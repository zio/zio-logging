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

import java.io.{ BufferedWriter, FileOutputStream, OutputStreamWriter, Writer }
import java.nio.charset.Charset
import java.nio.file.Path

private[logging] class FileWriter(
  destination: Path,
  charset: Charset,
  autoFlushBatchSize: Int,
  bufferedIOSize: Option[Int]
) extends Writer {
  private val writer: Writer = {
    val output = new OutputStreamWriter(new FileOutputStream(destination.toFile, true), charset)
    bufferedIOSize match {
      case Some(bufferSize) => new BufferedWriter(output, bufferSize)
      case None             => output
    }
  }

  private var entriesWritten: Long = 0

  final def write(buffer: Array[Char], offset: Int, length: Int): Unit =
    writer.write(buffer, offset, length)

  final def writeln(line: String): Unit = {
    writer.write(line)
    writer.write(System.lineSeparator)

    entriesWritten += 1

    if (entriesWritten % autoFlushBatchSize == 0)
      writer.flush()
  }

  final def flush(): Unit = writer.flush()

  final def close(): Unit = writer.close()
}
