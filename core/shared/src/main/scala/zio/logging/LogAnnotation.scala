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

import zio._

import java.{ util => ju }

/**
 * A `LogAnnotation` describes a particular type of statically-typed log
 * annotation applied to log lines. Log annotations combine in user-defined
 * ways, which means they can have arbitrary structure. In the end, however,
 * it must be possible to render each log annotation as a string.
 *
 * Applying an annotation to an effect with `@@` stores the value in the log context
 * ([[zio.logging.logContext]]) while the effect runs. When the context already has a value for the same
 * annotation, the two are merged with `combine(oldValue, newValue)`. Formats such as
 * `LogFormat.logAnnotation` and `LogFormat.allAnnotations` write the value as `name=render(value)`.
 *
 * {{{
 * import zio._
 * import zio.logging.LogAnnotation
 *
 * val orderId: LogAnnotation[Long] =
 *   LogAnnotation[Long](name = "order_id", combine = (_, newer) => newer, render = _.toString)
 *
 * val program: UIO[Unit] =
 *   ZIO.logInfo("Order shipped") @@ orderId(1042L) @@ LogAnnotation.UserId("jdoe")
 * }}}
 *
 * @see [[https://zio.dev/zio-logging/logger-context-and-annotations/ Logger Context and Annotations]]
 */
final case class LogAnnotation[A: Tag](name: String, combine: (A, A) => A, render: A => String) {
  self =>

  /** The type of the identity of this annotation, which `id` returns. */
  type Id

  /** The type of the annotation's values. */
  type Type = A

  /**
   * Returns an aspect that adds `value` for this annotation to the log context while the effect it is
   * applied to runs. A value already in the context for this annotation is merged with `value` through
   * `combine`.
   */
  def apply(value: A): ZIOAspect[Nothing, Any, Nothing, Any, Nothing, Any] =
    LogAnnotation.LogAnnotationAspect(Map(self -> value))

  /**
   * The identity of this annotation: its name together with its tag. Annotations with the same id are equal,
   * whatever their `combine` and `render` functions.
   */
  def id: Id = (name, tag).asInstanceOf[Id]

  /**
   * The `Tag` of the annotation type. Together with `name`, it identifies the annotation, so annotations
   * that share a name but differ in type are different annotations.
   */
  def tag: Tag[A] = implicitly[Tag[A]]

  override def hashCode: Int = id.hashCode

  override def equals(that: Any): Boolean =
    that match {
      case that: LogAnnotation[_] => self.id == that.id
      case _                      => false
    }

  override def toString: String = s"LogAnnotation($name, $tag)"
}

/**
 * The predefined log annotations `TraceId`, `TraceSpans` and `UserId`.
 */
object LogAnnotation {

  private[logging] final case class LogAnnotationAspect(annotations: Map[LogAnnotation[_], Any])
      extends ZIOAspect[Nothing, Any, Nothing, Any, Nothing, Any] {

    override def >>>[LowerR, UpperR, LowerE, UpperE, LowerA, UpperA](
      that: ZIOAspect[LowerR, UpperR, LowerE, UpperE, LowerA, UpperA]
    ): ZIOAspect[LowerR, UpperR, LowerE, UpperE, LowerA, UpperA] =
      that match {
        case LogAnnotationAspect(thatAnnotations) => LogAnnotationAspect(annotations ++ thatAnnotations)
        case that                                 => super.andThen(that)
      }

    final def apply[R, E, A](zio: ZIO[R, E, A])(implicit trace: Trace): ZIO[R, E, A] =
      logContext.getWith { context =>
        logContext.locally {
          annotations.foldLeft(context) { case (context, (annotation, value)) =>
            context.annotate(annotation.asInstanceOf[LogAnnotation[Any]], value)
          }
        }(zio)
      }
  }

  /**
   * The `TraceId` annotation keeps track of distributed trace id. Its name is `trace_id`; an inner value
   * replaces an outer one.
   */
  val TraceId: LogAnnotation[ju.UUID] = LogAnnotation[java.util.UUID](
    name = "trace_id",
    combine = (_: ju.UUID, r: ju.UUID) => r,
    render = _.toString
  )

  /**
   * The `TraceSpans` annotation keeps track of distributed spans. Its name is `trace_spans`; an inner value
   * is appended to the outer one, and the list is rendered joined with `:`, as in `outer:inner`.
   */
  val TraceSpans: LogAnnotation[List[String]] = LogAnnotation[List[String]](
    name = "trace_spans",
    combine = (l, r) => l ++ r,
    render = _.mkString(":")
  )

  /**
   * The `UserId` annotation keeps track of user id. Its name is `user_id`; an inner value replaces an outer
   * one.
   */
  val UserId: LogAnnotation[String] = LogAnnotation[String](
    name = "user_id",
    combine = (_: String, r: String) => r,
    render = _.toString
  )
}
