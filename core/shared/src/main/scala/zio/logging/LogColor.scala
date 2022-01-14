/*
 * Copyright 2019-2022 John A. De Goes and the ZIO Contributors
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

import scala.io.AnsiColor

final case class LogColor private (private[logging] val ansi: String) extends AnyVal

object LogColor {
  val RED: LogColor     = LogColor(AnsiColor.RED)
  val BLUE: LogColor    = LogColor(AnsiColor.BLUE)
  val YELLOW: LogColor  = LogColor(AnsiColor.YELLOW)
  val CYAN: LogColor    = LogColor(AnsiColor.CYAN)
  val GREEN: LogColor   = LogColor(AnsiColor.GREEN)
  val MAGENTA: LogColor = LogColor(AnsiColor.MAGENTA)
  val WHITE: LogColor   = LogColor(AnsiColor.WHITE)
  val RESET: LogColor   = LogColor(AnsiColor.RESET)
}
