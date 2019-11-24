package zio.logging

import zio._

final class ContextMap private (private val map: FiberRef[ContextMap.CMap]) {

  def get[V](key: ContextKey[V]): UIO[V] =
    map.get.map(ContextMap.get(_)(key))

  def add[V](key: ContextKey[V], value: V): UIO[Unit] =
    map.update(ContextMap.add(_)(key, value)).unit

  def merge(other: ContextMap): UIO[Unit] =
    for {
      otherMap <- other.map.get
      _        <- map.update(ContextMap.merge(_, otherMap))
    } yield ()

}

object ContextMap {
  // underlying untyped map
  private[ContextMap] type CMap = Map[ContextKey[Any], Any]

  private[ContextMap] def get[V](map: CMap)(key: ContextKey[V]): V =
    map.getOrElse(key.asInstanceOf[ContextKey[Any]], key.initial).asInstanceOf[V]

  private[ContextMap] def add[V](map: CMap)(key: ContextKey[V], value: V): CMap =
    map + (key.asInstanceOf[ContextKey[Any]] -> key.combine(get(map)(key), value).asInstanceOf[Any])

  private[ContextMap] def merge(first: CMap, second: CMap) =
    second.foldLeft(first) {
      case (m, (k, v)) =>
        add(m)(k, v)
    }

  val empty = FiberRef
    .make(Map.empty[ContextKey[Any], AnyRef], merge)
    .map(fiber => new ContextMap(fiber))
}
