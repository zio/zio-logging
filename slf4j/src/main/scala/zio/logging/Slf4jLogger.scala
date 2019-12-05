package zio.logging

import org.slf4j.LoggerFactory
import zio.internal.Tracing
import zio.internal.stacktracer.Tracer
import zio.internal.stacktracer.ZTraceElement.{ NoLocation, SourceLocation }
import zio.internal.stacktracer.impl.AkkaLineNumbersTracer
import zio.internal.tracing.TracingConfig
import zio.{ Cause, ZIO }

class Slf4jLogger extends AbstractLogging.Service[Any] {

  // this is copy from PlatformLive.
  private val tracing = Tracing(Tracer.globallyCached(new AkkaLineNumbersTracer), TracingConfig.enabled)

  private def logger(lambda: AnyRef) = tracing.tracer.traceLocation(lambda) match {
    case SourceLocation(_, clazz, _, _) => ZIO.effectTotal(LoggerFactory.getLogger(clazz))
    case NoLocation(_)                  => ZIO.effectTotal(LoggerFactory.getLogger("ZIO.defaultLogger"))
  }

  override def trace[Message](message: => Message): ZIO[Any with LoggingFormat[Message], Nothing, Unit] =
    for {
      l <- logger(() => message)
      _ <- ZIO.when(l.isTraceEnabled())(
            ZIO
              .accessM[LoggingFormat[Message]](_.format(message))
              .flatMap(msg => ZIO.effectTotal(l.trace(msg)))
          )
    } yield ()

  override def debug[Message](message: => Message): ZIO[LoggingFormat[Message], Nothing, Unit] =
    for {
      l <- logger(() => message)
      _ <- ZIO.when(l.isDebugEnabled())(
            ZIO
              .accessM[LoggingFormat[Message]](_.format(message))
              .flatMap(msg => ZIO.effectTotal(l.debug(msg)))
          )
    } yield ()

  override def info[Message](message: => Message): ZIO[LoggingFormat[Message], Nothing, Unit] =
    for {
      l <- logger(() => message)
      _ <- ZIO.when(l.isInfoEnabled())(
            ZIO
              .accessM[LoggingFormat[Message]](_.format(message))
              .flatMap(msg => ZIO.effectTotal(l.info(msg)))
          )
    } yield ()

  override def warning[Message](message: => Message): ZIO[LoggingFormat[Message], Nothing, Unit] =
    for {
      l <- logger(() => message)
      _ <- ZIO.when(l.isWarnEnabled())(
            ZIO
              .accessM[LoggingFormat[Message]](_.format(message))
              .flatMap(msg => ZIO.effectTotal(l.warn(msg)))
          )
    } yield ()

  override def error[Message](message: => Message): ZIO[LoggingFormat[Message], Nothing, Unit] =
    for {
      l <- logger(() => message)
      _ <- ZIO.when(l.isErrorEnabled())(
            ZIO
              .accessM[LoggingFormat[Message]](_.format(message))
              .flatMap(msg => ZIO.effectTotal(l.error(msg)))
          )
    } yield ()

  override def error[Message](message: => Message, cause: Cause[Any]): ZIO[LoggingFormat[Message], Nothing, Unit] =
    for {
      l <- logger(() => message)
      _ <- ZIO.when(l.isErrorEnabled())(
            ZIO
              .accessM[LoggingFormat[Message]](_.format(message))
              .flatMap(msg => ZIO.effectTotal(l.error(msg + " cause: " + cause.prettyPrint)))
          )
    } yield ()
}
