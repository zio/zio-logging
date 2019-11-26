package zio.logging

import zio._
trait LoggingFormat[Message] {
  def format(message: Message): ZIO[Any, Nothing, String]
}
