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
package zio.logging

import zio.{ Chunk, Config }

import scala.io.AnsiColor

/**
 * An ANSI terminal color, used by `LogFormat#color` and `LogFormat#highlight`. The values are the constants
 * in the `LogColor` companion object, such as `LogColor.RED`.
 */
final case class LogColor private (private[logging] val ansi: String) extends AnyVal

/**
 * The supported colors, and their config reader.
 */
object LogColor {

  /** Red, ANSI code `ESC[31m`. */
  val RED: LogColor = LogColor(AnsiColor.RED)

  /** Blue, ANSI code `ESC[34m`. */
  val BLUE: LogColor = LogColor(AnsiColor.BLUE)

  /** Yellow, ANSI code `ESC[33m`. */
  val YELLOW: LogColor = LogColor(AnsiColor.YELLOW)

  /** Cyan, ANSI code `ESC[36m`. */
  val CYAN: LogColor = LogColor(AnsiColor.CYAN)

  /** Green, ANSI code `ESC[32m`. */
  val GREEN: LogColor = LogColor(AnsiColor.GREEN)

  /** Magenta, ANSI code `ESC[35m`. */
  val MAGENTA: LogColor = LogColor(AnsiColor.MAGENTA)

  /** White, ANSI code `ESC[37m`. */
  val WHITE: LogColor = LogColor(AnsiColor.WHITE)

  /** The ANSI code `ESC[0m`, which resets the color. */
  val RESET: LogColor = LogColor(AnsiColor.RESET)

  private[logging] val logColorMapping: Map[String, LogColor] = Map(
    "RED"     -> LogColor.RED,
    "BLUE"    -> LogColor.BLUE,
    "YELLOW"  -> LogColor.YELLOW,
    "CYAN"    -> LogColor.CYAN,
    "GREEN"   -> LogColor.GREEN,
    "MAGENTA" -> LogColor.MAGENTA,
    "WHITE"   -> LogColor.WHITE,
    "RESET"   -> LogColor.RESET
  )

  private[logging] def logColorValue(value: String): Either[Config.Error.InvalidData, LogColor] =
    logColorMapping.get(value.toUpperCase) match {
      case Some(v) => Right(v)
      case None    => Left(Config.Error.InvalidData(Chunk.empty, s"Expected a LogColor, but found ${value}"))
    }

  /**
   * Reads a color by name, in any case, such as `RED` or `red`. Other names fail with
   * `Expected a LogColor, but found ` followed by the name.
   */
  val config: Config[LogColor] = Config.string.mapOrFail(logColorValue)
}
