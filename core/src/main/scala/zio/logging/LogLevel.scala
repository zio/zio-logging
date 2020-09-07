package zio.logging

/**
 * A log level defines the level at which an element is logged.
 */
sealed trait LogLevel { self =>
  def render: String
  def level: Int

  def >(that: LogLevel): Boolean  = self.level > that.level
  def >=(that: LogLevel): Boolean = self.level >= that.level
  def <(that: LogLevel): Boolean  = self.level < that.level
  def <=(that: LogLevel): Boolean = self.level <= that.level

  def max(that: LogLevel): LogLevel = if (self < that) that else self

  def min(that: LogLevel): LogLevel = if (self > that) that else self
}
// format: off
object LogLevel {
// format: on
  case object Fatal extends LogLevel { val level = 6; val render = "fatal" }
  case object Error extends LogLevel { val level = 5; val render = "error" }
  case object Warn  extends LogLevel { val level = 4; val render = "warn"  }
  case object Info  extends LogLevel { val level = 3; val render = "info"  }
  case object Debug extends LogLevel { val level = 2; val render = "debug" }
  case object Trace extends LogLevel { val level = 1; val render = "trace" }
  case object Off   extends LogLevel { val level = 0; val render = "off"   }
}
