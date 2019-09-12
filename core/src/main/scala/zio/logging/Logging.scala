package zio.logging

import zio.{ Cause, FiberRef, UIO, ZIO }

trait AbstractLogging[Message] {
  def logging: AbstractLogging.Service[Any, Message]
}

object AbstractLogging {

  trait Service[-R, Message] {
    def context: LoggingContext[Message]
    def trace(message: Message): ZIO[R, Nothing, Unit]
    def debug(message: Message): ZIO[R, Nothing, Unit]
    def info(message: Message): ZIO[R, Nothing, Unit]
    def warning(message: Message): ZIO[R, Nothing, Unit]
    def error(message: Message): ZIO[R, Nothing, Unit]
    def error(message: Message, cause: Cause[Any]): ZIO[R, Nothing, Unit]
  }

  final class LoggingContext[Message] private (ctx: FiberRef[Map[String, Message]]) {

    def apply(items: (String, Message)*): UIO[Unit] =
      ctx.update(_ ++ items).unit
  }

  object LoggingContext {

    def make[Message]: UIO[LoggingContext[Message]] =
      FiberRef
        .make(Map.empty[String, Message])
        .map(new LoggingContext[Message](_))
  }
}
trait Logging extends AbstractLogging[String]

object Logging {
  type Service[-R] = AbstractLogging.Service[R, String]
}
