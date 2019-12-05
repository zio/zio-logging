package zio.logging

import zio._

final class ContextMap private (private val map: FiberRef[ContextMap.CMap]) extends LoggingContext.Service[Any] {

  def get[V](key: ContextKey[V]): UIO[V] =
    map.get.map(ContextMap.get(_)(key)._2)

  def set[V](key: ContextKey[V], value: V): UIO[Unit] =
    map.update(ContextMap.set(_)(key, value)).unit

  def remove(key: ContextKey[_]): UIO[Unit] =
    map.update(ContextMap.remove(_)(key)).unit

  def locally[R, E, A, V](key: ContextKey[V], value: V)(zio: ZIO[R, E, A]): ZIO[R, E, A] =
    for {
      oldValue <- map.get
      newValue = ContextMap.add(oldValue)(key, value).mapValues(old => (Nil, old._2))
      b <- map
            .set(newValue)
            .bracket_(
              for {
                changes <- map.get.map(_.mapValues(_._1))
                _       <- map.set(ContextMap.merge(oldValue, changes))
              } yield ()
            )(zio)
    } yield b

  override def span[R1 <: Any, E, A, V](key: ContextKey[V], value: V)(zio: ZIO[R1, E, A]): ZIO[R1, E, A] =
    locally(key, value)(zio)
}

object ContextMap {
  sealed trait Action[+T]
  object Action {
    final case class Add[T](value: T) extends Action[T]
    final case class Set[T](value: T) extends Action[T]
    case object Remove                extends Action[Nothing]
  }

  // underlying untyped map
  private[ContextMap] type CMap = Map[ContextKey[Any], (List[Action[Any]], Any)]

  private[ContextMap] def get[V](map: CMap)(key: ContextKey[V]): (List[Action[V]], V) =
    map.getOrElse(key.asInstanceOf[ContextKey[Any]], (Nil, key.initial)).asInstanceOf[(List[Action[V]], V)]

  private[ContextMap] def set[V](map: CMap)(key: ContextKey[V], value: V): CMap = {
    val (oldChanges, _) = get(map)(key)
    map + (key.asInstanceOf[ContextKey[Any]] -> (Action.Set(value) :: oldChanges, value)
      .asInstanceOf[(List[Action[Any]], Any)])
  }

  private[ContextMap] def add[V](map: CMap)(key: ContextKey[V], value: V): CMap = {
    val (oldChanges, oldV) = get(map)(key)
    map + (key.asInstanceOf[ContextKey[Any]] -> (Action.Add(value) :: oldChanges, key.combine(oldV, value))
      .asInstanceOf[(List[Action[Any]], Any)])
  }

  private[ContextMap] def remove(map: CMap)(key: ContextKey[_]): CMap = {
    val (oldChanges, _) = get(map)(key)
    map + (key.asInstanceOf[ContextKey[Any]] -> (Action.Remove :: oldChanges, key.initial)
      .asInstanceOf[(List[Action[Any]], Any)])
  }

  private[ContextMap] def merge(map: CMap, changes: Map[ContextKey[Any], List[Action[Any]]]): CMap =
    changes.foldLeft(map) {
      case (m, (k, v)) =>
        val (oldChanges, oldValue) = m.getOrElse(k, (Nil, k.initial))
        m + (k -> v.foldRight((oldChanges, oldValue)) {
          case (c, (cs, v1)) =>
            (c :: cs, c match {
              case Action.Add(v2) => k.combine(v1, v2)
              case Action.Set(v2) => v2
              case Action.Remove  => k.initial
            })
        })
    }

  val empty = FiberRef
    .make(Map.empty[ContextKey[Any], (List[Action[Any]], Any)], (c1: CMap, c2: CMap) => merge(c1, c2.mapValues(_._1)))
    .map(fiber => new ContextMap(fiber))
}
