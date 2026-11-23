/*
 * Copyright 2019-2026 John A. De Goes and the ZIO Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package zio.logging

/**
 * A `LogContext` stores context associated with logging operations: a value for each typed
 * `LogAnnotation` that is in effect. The fiber reference [[zio.logging.logContext]] holds the current
 * context, and applying an annotation with `@@` adds to it.
 */
final case class LogContext private (private val map: Map[LogAnnotation[_], Any]) { self =>

  /**
   * Merges this context with the specified context.
   */
  def ++(that: LogContext): LogContext = self merge that

  /**
   * Annotates the context with the specified annotation and value, returning
   * the new context. When the context already has a value for the annotation,
   * the new value is merged into it with the annotation's `combine` function.
   */
  def annotate[A](annotation: LogAnnotation[A], newA: A): LogContext =
    get(annotation) match {
      case None       => new LogContext(map + (annotation -> newA))
      case Some(oldA) => new LogContext(map + (annotation -> annotation.combine(oldA, newA)))
    }

  /**
   * Renders value for given annotation
   */
  def apply[A](logAnnotation: LogAnnotation[A]): Option[String] =
    get(logAnnotation).map(logAnnotation.render(_))

  /**
   * Retrieves the specified annotation from the context.
   */
  def get[A](annotation: LogAnnotation[A]): Option[A] =
    map.get(annotation).map(_.asInstanceOf[A])

  /**
   * Retrieves the specified annotation by name from the context, return rendered value.
   */
  def get(name: String): Option[String] =
    map.collectFirst {
      case (annotation: LogAnnotation[Any], value: Any) if (annotation.name == name) =>
        annotation.render(value)
    }

  /**
   * Merges this context with the specified context. For an annotation present in both, the values are
   * merged with the annotation's `combine` function, this context's value first.
   */
  def merge(that: LogContext): LogContext = {
    val allKeys = self.map.keySet ++ that.map.keySet

    new LogContext(
      allKeys.foldLeft(Map.empty[LogAnnotation[_], Any]) { case (map, annotation) =>
        map +
          (annotation -> ((self.map.get(annotation), that.map.get(annotation)) match {
            case (Some(l), Some(r)) =>
              annotation.combine(l.asInstanceOf[annotation.Type], r.asInstanceOf[annotation.Type])
            case (None, Some(r))    => r
            case (Some(l), None)    => l
            case (None, None)       => throw new IllegalStateException("Impossible")
          }))
      }
    )
  }

  /**
   * Renders all log annotations in the context.
   */
  def asMap: Map[String, String] =
    map.asInstanceOf[Map[LogAnnotation[Any], Any]].map { case (annotation, value) =>
      annotation.name -> annotation.render(value)
    }
}

/**
 * Constructors for `LogContext`.
 */
object LogContext {

  /**
   * An empty context.
   */
  val empty: LogContext = new LogContext(Map())
}
