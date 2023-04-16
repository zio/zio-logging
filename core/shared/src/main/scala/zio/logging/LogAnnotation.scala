/*
 * Copyright 2019-2023 John A. De Goes and the ZIO Contributors
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
 * {{{
 * myEffect @@ UserId("jdoe")
 * }}}
 */
final case class LogAnnotation[A: Tag](name: String, combine: (A, A) => A, render: A => String) {
  self =>
  type Id
  type Type = A

  sealed trait LogAnnotationAspect extends ZIOAspect[Nothing, Any, Nothing, Any, Nothing, Any] {
    private[zio] def annotations: Map[LogAnnotation[_], Any]

    final def apply[R, E, A](zio: ZIO[R, E, A])(implicit trace: Trace): ZIO[R, E, A] =
      logContext.getWith { context =>
        logContext.locally {
          annotations.foldLeft(context) { case (context, (annotation, value)) =>
            context.annotate(annotation.asInstanceOf[LogAnnotation[Any]], value)
          }
        }(zio)
      }
  }

  def apply(value: A): LogAnnotationAspect =
    new LogAnnotationAspect {
      def annotations: Map[LogAnnotation[_], Any] = Map(self -> value)

      override def @@[LowerR, UpperR, LowerE, UpperE, LowerA, UpperA](
        that: ZIOAspect[LowerR, UpperR, LowerE, UpperE, LowerA, UpperA]
      ): ZIOAspect[LowerR, UpperR, LowerE, UpperE, LowerA, UpperA] =
        that match {
          case that: LogAnnotationAspect =>
            new LogAnnotationAspect {
              def annotations: Map[LogAnnotation[_], Any] =
                Map(self -> value).asInstanceOf[Map[LogAnnotation[_], Any]] ++ that.annotations
            }
          case that                      => super.andThen(that)
        }
    }

//  def apply(value: A): ZIOAspect[Nothing, Any, Nothing, Any, Nothing, Any] =
//    new ZIOAspect[Nothing, Any, Nothing, Any, Nothing, Any] {
//      def apply[R, E, A](zio: ZIO[R, E, A])(implicit trace: Trace): ZIO[R, E, A] =
//        logContext.get.flatMap(context => logContext.locally(context.annotate(self, value))(zio))
//    }

  def id: Id = (name, tag).asInstanceOf[Id]

  /**
   * The class tag of the annotation type, used for disambiguation purposes only.
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

object LogAnnotation {

  /**
   * The `TraceId` annotation keeps track of distributed trace id.
   */
  val TraceId: LogAnnotation[ju.UUID] = LogAnnotation[java.util.UUID](
    name = "trace_id",
    combine = (_: ju.UUID, r: ju.UUID) => r,
    render = _.toString
  )

  /**
   * The `TraceSpans` annotation keeps track of distributed spans.
   */
  val TraceSpans: LogAnnotation[List[String]] = LogAnnotation[List[String]](
    name = "trace_spans",
    combine = (l, r) => l ++ r,
    render = _.mkString(":")
  )

  /**
   * The `UserId` annotation keeps track of user id.
   */
  val UserId: LogAnnotation[String] = LogAnnotation[String](
    name = "user_id",
    combine = (_: String, r: String) => r,
    render = _.toString
  )
}
