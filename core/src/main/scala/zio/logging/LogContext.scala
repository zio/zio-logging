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
   * Renders value for given annotation
   */
  def apply[A](logAnnotation: LogAnnotation[A]): String =
    logAnnotation.render(get(logAnnotation))

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

    new LogContext(
      allKeys.foldLeft(Map.empty[LogAnnotation[_], Any]) { case (map, annotation) =>
        map +
          (annotation -> ((self.map.get(annotation), that.map.get(annotation)) match {
            case (Some(_), Some(_)) => annotation.combine(self.get(annotation), that.get(annotation))
            case (None, Some(_))    => that.get(annotation)
            case (Some(_), None)    => self.get(annotation)
            case (None, None)       => annotation.combine(self.get(annotation), that.get(annotation)) // this is no possible
          }))
      }
    )
  }

  /**
   * Renders all log annotations in current context.
   *
   * @return Map from annotation name to rendered value
   */
  def renderContext: Map[String, String] =
    map.asInstanceOf[Map[LogAnnotation[Any], Any]].map { case (annotation, value) =>
      annotation.name -> annotation.render(value)
    }
}

object LogContext {

  /**
   * An empty context.
   */
  val empty: LogContext = new LogContext(Map())
}
