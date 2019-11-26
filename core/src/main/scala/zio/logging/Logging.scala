package zio.logging

import zio.{ Cause, ZIO }

trait AbstractLogging[Message] {
  def logging: AbstractLogging.Service[Any, Message]
}

object AbstractLogging {

  trait Service[-R, Message] {
    def addToContext(key: String, value: Message): ZIO[R, Nothing, Unit]
    def removeFromContext(key: String): ZIO[R, Nothing, Unit]
    def trace(message: => Message): ZIO[R, Nothing, Unit]
    def debug(message: => Message): ZIO[R, Nothing, Unit]
    def info(message: => Message): ZIO[R, Nothing, Unit]
    def warning(message: => Message): ZIO[R, Nothing, Unit]
    def error(message: => Message): ZIO[R, Nothing, Unit]
    def error(message: => Message, cause: Cause[Any]): ZIO[R, Nothing, Unit]
  }

}
trait Logging extends AbstractLogging[String]

object Logging {
  type Service[-R] = AbstractLogging.Service[R, String]
}
