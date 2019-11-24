package zio.logging

import zio.{ Cause, FiberRef, UIO, ZIO }

import scala.reflect.ClassTag

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
  }

  class ContextMap private (private val map: FiberRef[Map[ContextKey[Any], AnyRef]]) {

    def get[V](key: ContextKey[V]): UIO[V] =
      map.get.map(
        _.getOrElse(key.asInstanceOf[ContextKey[Any]], key.initial).asInstanceOf[V]
      )

    def add[V](key: ContextKey[V], value: V): UIO[Unit] =
      map.update { oldMap =>
        val mapKey        = key.asInstanceOf[ContextKey[Any]]
        val maybeOldValue = oldMap.get(mapKey).map(oldValue => key.combine(oldValue.asInstanceOf[V], value))
        oldMap.updated(mapKey, maybeOldValue.getOrElse(value).asInstanceOf[AnyRef])
      }.unit
  }

  object ContextMap {

    val empty = FiberRef
      .make(Map.empty[ContextKey[Any], AnyRef])
      .map(fiber => new ContextMap(fiber))
  }

  class ContextKey[V] private (
    val identifier: String,
    val initial: V,
    val combine: (V, V) => V,
    private[logging] val classTag: ClassTag[V]
  )

  object ContextKey {

    def apply[V](
      identifier: String,
      initial: V,
      combine: (V, V) => V = (_: V, newValue: V) => newValue  // by default replace
    )(implicit classTag: ClassTag[V]): ContextKey[V] =
      new ContextKey(identifier, initial, combine, classTag)
  }

}
trait Logging extends AbstractLogging[String]

object Logging {
  type Service[-R] = AbstractLogging.Service[R, String]
}
