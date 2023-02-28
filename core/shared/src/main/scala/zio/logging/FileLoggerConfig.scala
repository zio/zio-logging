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
package zio.logging

import zio._

import java.net.URI
import java.nio.charset.{ Charset, StandardCharsets }
import java.nio.file.{ Path, Paths }
import scala.util.{ Failure, Success, Try }

final case class FileLoggerConfig(
  destination: Path,
  format: LogFormat,
  filter: LogFilter[String],
  charset: Charset,
  autoFlushBatchSize: Int,
  bufferedIOSize: Option[Int]
)

object FileLoggerConfig {

  def apply(
    destination: Path,
    pattern: LogPattern,
    filter: LogFilter[String],
    charset: Charset,
    autoFlushBatchSize: Int,
    bufferedIOSize: Option[Int]
  ): FileLoggerConfig =
    FileLoggerConfig(destination, pattern.toLogFormat, filter, charset, autoFlushBatchSize, bufferedIOSize)

  val config: Config[FileLoggerConfig] = {

    def pathValue(value: String): Either[Config.Error.InvalidData, Path] =
      Try(Paths.get(URI.create(value))) match {
        case Success(v) => Right(v)
        case Failure(_) =>
          Left(Config.Error.InvalidData(Chunk.empty, s"Expected a Path, but found ${value}"))
      }

    def charsetValue(value: String): Either[Config.Error.InvalidData, Charset] =
      Try(Charset.forName(value)) match {
        case Success(v) => Right(v)
        case Failure(_) =>
          Left(Config.Error.InvalidData(Chunk.empty, s"Expected a Charset, but found ${value}"))
      }

    val pathConfig               = Config.string.mapOrFail(pathValue).nested("path")
    val patternConfig            = LogPattern.config.nested("pattern")
    val filterConfig             = LogFilter.LogLevelByNameConfig.config.nested("filter")
    val charsetConfig            = Config.string.mapOrFail(charsetValue).nested("charset").withDefault(StandardCharsets.UTF_8)
    val autoFlushBatchSizeConfig = Config.int.nested("autoFlushBatchSize").withDefault(1)
    val bufferedIOSizeConfig     = Config.int.nested("bufferedIOSize").optional

    (pathConfig ++ patternConfig ++ filterConfig ++ charsetConfig ++ autoFlushBatchSizeConfig ++ bufferedIOSizeConfig).map {
      case (path, pattern, filterConfig, charset, autoFlushBatchSize, bufferedIOSize) =>
        FileLoggerConfig(
          path,
          pattern,
          LogFilter.logLevelByName(filterConfig),
          charset,
          autoFlushBatchSize,
          bufferedIOSize
        )
    }
  }
}
