package zio.logging

import zio.ZIO

trait Logging[Message] {
  def logging: Logging.Service[Any, Message]
}

object Logging {

  trait Service[-R, Message] {
    def log(line: => Message): ZIO[R, Nothing, Unit]
    def log(level: LogLevel)(line: => Message): ZIO[R, Nothing, Unit]
    def locallyAnnotate[A, R1 <: R, E, A1](annotation: LogAnnotation[A], value: A)(zio: ZIO[R1, E, A1]): ZIO[R1, E, A1]
  }

}
