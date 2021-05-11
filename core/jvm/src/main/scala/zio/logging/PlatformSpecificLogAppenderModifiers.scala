package zio.logging

import zio.internal.Tracing
import zio.internal.stacktracer.Tracer
import zio.internal.stacktracer.ZTraceElement.{ NoLocation, SourceLocation }
import zio.internal.stacktracer.impl.AkkaLineNumbersTracer
import zio.internal.tracing.TracingConfig
import zio.logging.LogAppender.Service
import zio.{ Tag, UIO, ZLayer }

trait PlatformSpecificLogAppenderModifiers {
  private val tracing = Tracing(Tracer.globallyCached(new AkkaLineNumbersTracer), TracingConfig.enabled)

  private def classNameForLambda(lambda: => AnyRef): Option[String] =
    tracing.tracer.traceLocation(() => lambda) match {
      case SourceLocation(_, clazz, _, _) => Some(clazz)
      case NoLocation(_)                  => None
    }

  def withLoggerNameFromLine[A <: AnyRef](implicit
    tag: Tag[LogAppender.Service[A]]
  ): ZLayer[Appender[A], Nothing, Appender[A]] =
    ZLayer.fromFunction[Appender[A], LogAppender.Service[A]](appender =>
      new Service[A] {
        override def write(ctx: LogContext, msg: => A): UIO[Unit] = {
          val ctxWithName = ctx.get(LogAnnotation.Name) match {
            case Nil =>
              ctx.annotate(
                LogAnnotation.Name,
                classNameForLambda(msg).getOrElse("ZIO.defaultLogger") :: Nil
              )
            case _   => ctx
          }
          appender.get.write(ctxWithName, msg)
        }
      }
    )
}
