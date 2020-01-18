package zio.logging

import zio.clock._
import zio.console._
import zio.internal.Tracing
import zio.internal.stacktracer.Tracer
import zio.internal.stacktracer.ZTraceElement.{ NoLocation, SourceLocation }
import zio.internal.stacktracer.impl.AkkaLineNumbersTracer
import zio.internal.tracing.TracingConfig
import zio.{ FiberRef, UIO, ZIO }

/**
 * A logger of strings.
 */
trait Logger extends LoggerLike[String]
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
  def make(logLevel: LogLevel, logger: (LogContext, => String) => UIO[Unit]): UIO[Logger] =
    FiberRef
      .make(LogContext.empty)
      .flatMap(ref => ref.update(ctx => ctx.annotate(LogAnnotation.Level, logLevel)).as(ref))
      .map { ref =>
        new Logger {
          def locally[R, E, A](f: LogContext => LogContext)(zio: ZIO[R, E, A]): ZIO[R, E, A] =
            ref.get.flatMap(context => ref.locally(f(context))(zio))

          def log(line: => String): UIO[Unit] = ref.get.flatMap(context => logger(context, line))

          def logContext: UIO[LogContext] = ref.get
        }
      }

  val noopLogger =
    make(LogLevel.Off, (_, _) => ZIO.unit)

  def consoleLogger(logLevel: LogLevel, format: (LogContext, => String) => String) =
    ZIO.accessM[Clock with Console](env =>
      make(
        logLevel,
        (context, line) =>
          (for {
            date       <- currentDateTime
            level      = context.get(LogAnnotation.Level)
            loggerName = context.get(LogAnnotation.Name) match {
              case Nil => classNameForLambda(line).getOrElse("ZIO.defaultLogger")
              case _ => LogAnnotation.Name.render(context.get(LogAnnotation.Name))
            }
            _          <- putStrLn(date.toString + " " + level.render + " " + loggerName + " " + format(context, line))

          } yield ()).provide(env)
      )
    )
}
