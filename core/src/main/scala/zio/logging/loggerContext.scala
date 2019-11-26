package zio.logging

import zio.ZIO

object loggerContext extends LoggingContext.Service[LoggingContext] {
  override def get[V](key: ContextKey[V]): ZIO[LoggingContext, Nothing, V] =
    ZIO.accessM[LoggingContext](_.loggingContext.get(key))

  override def add[V](key: ContextKey[V], value: V): ZIO[LoggingContext, Nothing, Unit] =
    ZIO.accessM[LoggingContext](_.loggingContext.add(key, value))

  override def set[V](key: ContextKey[V], value: V): ZIO[LoggingContext, Nothing, Unit] =
    ZIO.accessM[LoggingContext](_.loggingContext.set(key, value))

  override def remove(key: ContextKey[_]): ZIO[LoggingContext, Nothing, Unit] =
    ZIO.accessM[LoggingContext](_.loggingContext.remove(key))

  override def span[R1 <: LoggingContext, E, A, V](key: ContextKey[V], value: V)(zio: ZIO[R1, E, A]): ZIO[R1, E, A] =
    ZIO.accessM[R1](_.loggingContext.span(key, value)(zio))
}
