package zio.logging

import zio.{ Cause, ZIO }

trait AbstractLogging {
  def logging: AbstractLogging.Service[Any]
}

object AbstractLogging {

  trait Service[-R] {

    def trace[Message](message: => Message): ZIO[R with LoggingFormat[Message] , Nothing, Unit]
    def debug[Message](message: => Message): ZIO[R with LoggingFormat[Message], Nothing, Unit]
    def info[Message](message: => Message): ZIO[R with LoggingFormat[Message], Nothing, Unit]
    def warning[Message](message: => Message): ZIO[R with LoggingFormat[Message], Nothing, Unit]
    def error[Message](message: => Message): ZIO[R with LoggingFormat[Message], Nothing, Unit]
    def error[Message](message: => Message, cause: Cause[Any]): ZIO[R with LoggingFormat[Message], Nothing, Unit]
  }

}
