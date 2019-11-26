package zio.logging

import zio.{ Cause, ZIO }

trait AbstractLogging[Message] {
  def logging: AbstractLogging.Service[Any, Message]
}

object AbstractLogging {

  trait Service[-R, Message] {
    def loggingContext: ContextMap

    def trace(message: => Message): ZIO[R, Nothing, Unit]
    def debug(message: Message): ZIO[R, Nothing, Unit]
    def info(message: Message): ZIO[R, Nothing, Unit]
    def warning(message: Message): ZIO[R, Nothing, Unit]
    def error(message: Message): ZIO[R, Nothing, Unit]
    def error(message: Message, cause: Cause[Any]): ZIO[R, Nothing, Unit]

    def span[R1 <: R, E, A, V](key: ContextKey[V], value: V)(zio: ZIO[R1, E, A]): ZIO[R1, E, A] =
      loggingContext.locally(key, value)(zio)
  }

}
