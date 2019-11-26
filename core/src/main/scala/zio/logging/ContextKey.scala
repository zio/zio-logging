package zio.logging

import scala.reflect.ClassTag

final class ContextKey[V] private (
  val identifier: String,
  val initial: V,
  val combine: (V, V) => V,
  private[logging] val classTag: ClassTag[V]
)

object ContextKey {

  def apply[V](
    identifier: String,
    initial: V,
    combine: (V, V) => V = (_: V, newValue: V) => newValue // by default replace
  )(implicit classTag: ClassTag[V]): ContextKey[V] =
    new ContextKey(identifier, initial, combine, classTag)
}
