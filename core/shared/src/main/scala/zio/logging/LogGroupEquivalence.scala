package zio.logging

final case class LogGroupEquivalence[-A](equivalent: (A, A) => Boolean) {

  final def &&[B <: A](other: LogGroupEquivalence[B]): LogGroupEquivalence[B] =
    and(other)

  final def ||[B <: A](other: LogGroupEquivalence[B]): LogGroupEquivalence[B] =
    or(other)

  final def and[B <: A](other: LogGroupEquivalence[B]): LogGroupEquivalence[B] =
    LogGroupEquivalence[B] { (l, r) =>
      equivalent(l, r) && other.equivalent(l, r)
    }

  final def contramap[B](f: B => A): LogGroupEquivalence[B] =
    LogGroupEquivalence { (l, r) =>
      equivalent(f(l), f(r))
    }

  final def not: LogGroupEquivalence[A] =
    LogGroupEquivalence[A]((l, r) => !equivalent(l, r))

  final def or[B <: A](other: LogGroupEquivalence[B]): LogGroupEquivalence[B] =
    LogGroupEquivalence[B] { (l, r) =>
      equivalent(l, r) || other.equivalent(l, r)
    }

  final def unary_! : LogGroupEquivalence[A] = not
}

object LogGroupEquivalence {

  val default: LogGroupEquivalence[Any] = LogGroupEquivalence[Any](_ == _)

  def listStartWith[A]: LogGroupEquivalence[List[A]] = LogGroupEquivalence(_.startsWith(_))

  val stringStartWith: LogGroupEquivalence[String] = LogGroupEquivalence(_.startsWith(_))
}
