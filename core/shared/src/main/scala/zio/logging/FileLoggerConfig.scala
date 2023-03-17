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
  charset: Charset = StandardCharsets.UTF_8,
  autoFlushBatchSize: Int = 1,
  bufferedIOSize: Option[Int] = None,
  rollingPolicy: Option[FileLoggerConfig.FileRollingPolicy] = None
)

object FileLoggerConfig {
  sealed trait FileRollingPolicy
  object FileRollingPolicy {
    case object TimeBasedRollingPolicy extends FileRollingPolicy

    private[logging] val logLevelMapping: Map[String, FileRollingPolicy] = Map(
      "TimeBasedRollingPolicy" -> FileRollingPolicy.TimeBasedRollingPolicy
    )

    private[logging] def fileRollingPolicyValue(value: String): Either[Config.Error.InvalidData, FileRollingPolicy] =
      logLevelMapping.get(value.toUpperCase) match {
        case Some(v) => Right(v)
        case None    => Left(Config.Error.InvalidData(Chunk.empty, s"Expected a LogLevel, but found ${value}"))
      }
  }

  private def charsetValue(value: String): Either[Config.Error.InvalidData, Charset] =
    Try(Charset.forName(value)) match {
      case Success(v) => Right(v)
      case Failure(_) =>
        Left(Config.Error.InvalidData(Chunk.empty, s"Expected a Charset, but found ${value}"))
    }

  private def pathValue(value: String): Either[Config.Error.InvalidData, Path] =
    Try(Paths.get(URI.create(value))) match {
      case Success(v) => Right(v)
      case Failure(_) =>
        Left(Config.Error.InvalidData(Chunk.empty, s"Expected a Path, but found ${value}"))
    }

  val config: Config[FileLoggerConfig] = {
    val autoFlushBatchSizeConfig = Config.int.nested("autoFlushBatchSize").withDefault(1)
    val bufferedIOSizeConfig     = Config.int.nested("bufferedIOSize").optional
    val filterConfig             = LogFilter.LogLevelByNameConfig.config.nested("filter")
    val charsetConfig            =
      Config.string.mapOrFail(charsetValue).nested("charset").withDefault(StandardCharsets.UTF_8)
    val pathConfig               = Config.string.mapOrFail(pathValue).nested("path")
    val formatConfig             = LogFormat.config.nested("format").withDefault(LogFormat.default)
    val rollingPolicyConfig      =
      Config.string.mapOrFail(FileRollingPolicy.fileRollingPolicyValue).nested("format").optional

    (pathConfig ++ formatConfig ++ filterConfig ++ charsetConfig ++ autoFlushBatchSizeConfig ++ bufferedIOSizeConfig ++ rollingPolicyConfig).map {
      case (path, format, filterConfig, charset, autoFlushBatchSize, bufferedIOSize, rollingPolicy) =>
        FileLoggerConfig(
          path,
          format,
          LogFilter.logLevelByName(filterConfig),
          charset,
          autoFlushBatchSize,
          bufferedIOSize,
          rollingPolicy
        )
    }
  }

}
