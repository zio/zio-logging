package zio.logging

/**
 * A log level defines the level at which an element is logged.
 */
sealed trait LogLevel { self =>
  def render: String
  def level: Int

  def >(that: LogLevel)  = self.level > that.level
  def >=(that: LogLevel) = self.level >= that.level
  def <(that: LogLevel)  = self.level < that.level
  def <=(that: LogLevel) = self.level <= that.level

  def max(that: LogLevel): LogLevel = if (self < that) that else self

  def min(that: LogLevel): LogLevel = if (self > that) that else self
}
// format: off
object LogLevel {
// format: on
  case object Fatal extends LogLevel { val level = 6; val render = "FATAL" }
  case object Error extends LogLevel { val level = 5; val render = "ERROR" }
  case object Warn  extends LogLevel { val level = 4; val render = "WARN"  }
  case object Info  extends LogLevel { val level = 3; val render = "INFO"  }
  case object Debug extends LogLevel { val level = 2; val render = "DEBUG" }
  case object Trace extends LogLevel { val level = 1; val render = "TRACE" }
  case object Off   extends LogLevel { val level = 0; val render = "OFF"   }
}
