package zio.logging

import zio.{ FiberRefs, LogLevel, Trace, Zippable }

trait LogGroup[A] { self =>

  def apply(
    trace: Trace,
    logLevel: LogLevel,
    context: FiberRefs,
    annotations: Map[String, String]
  ): A

  final def map[B](f: A => B): LogGroup[B] = new LogGroup[B] {
    override def apply(trace: Trace, logLevel: LogLevel, context: FiberRefs, annotations: Map[String, String]): B =
      f(self(trace, logLevel, context, annotations))
  }

  final def zipWith[B, C](
    other: LogGroup[B]
  )(f: (A, B) => C): LogGroup[C] = new LogGroup[C] {
    override def apply(
      trace: Trace,
      logLevel: LogLevel,
      context: FiberRefs,
      annotations: Map[String, String]
    ): C =
      f(self(trace, logLevel, context, annotations), other(trace, logLevel, context, annotations))
  }

  final def ++[B](
    other: LogGroup[B]
  )(implicit zippable: Zippable[A, B]): LogGroup[zippable.Out] = new LogGroup[zippable.Out] {
    override def apply(
      trace: Trace,
      logLevel: LogLevel,
      context: FiberRefs,
      annotations: Map[String, String]
    ): zippable.Out =
      zippable.zip(self(trace, logLevel, context, annotations), other(trace, logLevel, context, annotations))
  }

}

object LogGroup {
  val level: LogGroup[LogLevel] =
    (_, logLevel, _, _) => logLevel

  val loggerName: LogGroup[String] =
    (trace, _, _, _) => getLoggerName()(trace)

  val loggerNameAndLevel: LogGroup[(String, LogLevel)] =
    (trace, logLevel, _, _) => getLoggerName()(trace) -> logLevel
}
