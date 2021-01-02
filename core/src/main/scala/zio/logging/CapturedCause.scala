package zio.logging

import zio.Cause

sealed trait CapturedCause {
  val cause: Cause[Any]
  def toThrowable: Throwable
}

object CapturedCause {
  def apply[E: CauseToThrowable](c: Cause[E]): CapturedCause =
    new CapturedCause {
      override val cause: Cause[Any]      = c
      override def toThrowable: Throwable =
        implicitly[CauseToThrowable[E]].toThrowable(c)
    }

  trait CauseToThrowable[-E] {
    def toThrowable(cause: Cause[E]): Throwable
  }

  implicit def throwableCause[E <: Throwable]: CauseToThrowable[E] = (cause: Cause[E]) => cause.squash
  implicit val nothingCause: CauseToThrowable[Nothing]             = (_: Cause[Nothing]) => new RuntimeException("Nothing")

  object causeToRuntimeException {
    implicit def toThrowable[E]: CauseToThrowable[E] = (cause: Cause[E]) => new RuntimeException(cause.prettyPrint)
  }
}
