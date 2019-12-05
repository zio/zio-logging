package zio.logging

import zio.ZIO

trait LoggingContext {
  def loggingContext: LoggingContext.Service[Any]
}

object LoggingContext {
  trait Service[-R] {
    def get[V](key: ContextKey[V]): ZIO[R, Nothing, V]
    def set[V](key: ContextKey[V], value: V): ZIO[R, Nothing, Unit]
    def remove(key: ContextKey[_]): ZIO[R, Nothing, Unit]
    def span[R1 <: R, E, A, V](key: ContextKey[V], value: V)(zio: ZIO[R1, E, A]): ZIO[R1, E, A]
  }
}
