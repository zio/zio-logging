package zio.logging

final case class LogGroupEquivalence[-A](equivalent: (A, A) => Boolean)

object LogGroupEquivalence {

  val default: LogGroupEquivalence[Any] = LogGroupEquivalence[Any](_ == _)

  def listStartWith[A]: LogGroupEquivalence[List[A]] = LogGroupEquivalence(_.startsWith(_))

  val stringStartWith: LogGroupEquivalence[String] = LogGroupEquivalence(_.startsWith(_))
}
