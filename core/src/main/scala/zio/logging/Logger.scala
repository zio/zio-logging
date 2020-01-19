package zio.logging

import zio.internal.Tracing
import zio.internal.stacktracer.Tracer
import zio.internal.stacktracer.ZTraceElement.{ NoLocation, SourceLocation }
import zio.internal.stacktracer.impl.AkkaLineNumbersTracer
import zio.internal.tracing.TracingConfig
import zio.{ FiberRef, UIO, URIO, ZIO }

/**
 * A logger of strings.
 */
trait Logger[-R] extends LoggerLike[String, R]
object Logger {

  private val tracing = Tracing(Tracer.globallyCached(new AkkaLineNumbersTracer), TracingConfig.enabled)

  private[logging] def classNameForLambda(lambda: => AnyRef) =
    tracing.tracer.traceLocation(() => lambda) match {
      case SourceLocation(_, clazz, _, _) => Some(clazz)
      case NoLocation(_)                  => None
    }

  /**
   * Constructs a logger provided the specified sink.
   */
  def make[R](logLevel: LogLevel, logger: (LogContext, => String) => URIO[R, Unit]): URIO[R, Logger[R]] =
    FiberRef
      .make(LogContext.empty.annotate(LogAnnotation.Level, logLevel))
      .map { ref =>
        new Logger[R] {
          def locally[R1, E, A](f: LogContext => LogContext)(zio: ZIO[R1, E, A]): ZIO[R1, E, A] =
            ref.get.flatMap(context => ref.locally(f(context))(zio))

          def log(line: => String): URIO[R, Unit] = ref.get.flatMap(context => logger(context, line))

          def logContext: UIO[LogContext] = ref.get
        }
      }

}
