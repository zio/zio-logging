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
