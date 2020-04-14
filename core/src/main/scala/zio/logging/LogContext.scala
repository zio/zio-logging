package zio.logging

/**
 * A `LogContext` stores context associated with logging operations.
 */
final case class LogContext private (private val map: Map[LogAnnotation[_], Any]) { self =>

  /**
   * Merges this context with the specified context.
   */
  def ++(that: LogContext): LogContext = self merge that

  /**
   * Annotates the context with the specified annotation and value, returning
   * the new context.
   */
  def annotate[A](annotation: LogAnnotation[A], newA: A): LogContext = {
    val oldA = get(annotation)

    new LogContext(map + (annotation -> annotation.combine(oldA, newA)))
  }

  /**
   * Retrieves the specified annotation from the context.
   */
  def get[A](annotation: LogAnnotation[A]): A =
    map.get(annotation).fold(annotation.initialValue)(_.asInstanceOf[A])

  /**
   * Merges this context with the specified context.
   */
  def merge(that: LogContext): LogContext = {
    val allKeys = self.map.keySet ++ that.map.keySet

    new LogContext(allKeys.foldLeft(Map.empty[LogAnnotation[_], Any]) {
      case (map, annotation) =>
        map + (annotation -> annotation.combine(self.get(annotation), that.get(annotation)))
    })
  }

  def asStringMap: Map[String, String] = map.map {
    case (LogAnnotation(name, _, _, render), value) => (name, render(value))
  }
}

object LogContext {

  /**
   * An empty context.
   */
  val empty: LogContext = new LogContext(Map())
}
