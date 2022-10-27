package zio.logging

final case class LogGroupRelation[-A](related: (A, A) => Boolean) {

  final def &&[B <: A](other: LogGroupRelation[B]): LogGroupRelation[B] =
    and(other)

  final def ||[B <: A](other: LogGroupRelation[B]): LogGroupRelation[B] =
    or(other)

  final def and[B <: A](other: LogGroupRelation[B]): LogGroupRelation[B] =
    LogGroupRelation[B] { (l, r) =>
      related(l, r) && other.related(l, r)
    }

  final def contramap[B](f: B => A): LogGroupRelation[B] =
    LogGroupRelation { (l, r) =>
      related(f(l), f(r))
    }

  final def not: LogGroupRelation[A] =
    LogGroupRelation[A]((l, r) => !related(l, r))

  final def or[B <: A](other: LogGroupRelation[B]): LogGroupRelation[B] =
    LogGroupRelation[B] { (l, r) =>
      related(l, r) || other.related(l, r)
    }

  final def unary_! : LogGroupRelation[A] = not
}

object LogGroupRelation {

  val default: LogGroupRelation[Any] = LogGroupRelation[Any](_ == _)

  def listStartWith[A]: LogGroupRelation[List[A]] = LogGroupRelation(_.startsWith(_))

  val stringStartWith: LogGroupRelation[String] = LogGroupRelation(_.startsWith(_))
}
