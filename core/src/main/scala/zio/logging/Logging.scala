package zio.logging

import zio.{ Cause, ZIO }

trait Logging[Message] {
  def logging: Logging.Service[Any, Message]
}

object Logging {

  trait Service[-R, Message] {

    def trace(message: => Message): ZIO[R, Nothing, Unit]
    def debug(message: => Message): ZIO[R, Nothing, Unit]
    def info(message: => Message): ZIO[R, Nothing, Unit]
    def warning(message: => Message): ZIO[R, Nothing, Unit]
    def error(message: => Message): ZIO[R, Nothing, Unit]
    def error(message: => Message, cause: Cause[Any]): ZIO[R, Nothing, Unit]
  }

}
