package zio.logging.slf4j

import org.slf4j.LoggerFactory
import zio.internal.Tracing
import zio.internal.stacktracer.Tracer
import zio.internal.stacktracer.ZTraceElement.{ NoLocation, SourceLocation }
import zio.internal.stacktracer.impl.AkkaLineNumbersTracer
import zio.internal.tracing.TracingConfig
import zio.logging.Logging
import zio.{ Cause, ZIO }

trait Slf4jLogger extends Logging.Service[Any, String] {

  // this is copy from PlatformLive.
  private val tracing = Tracing(Tracer.globallyCached(new AkkaLineNumbersTracer), TracingConfig.enabled)

  private def logger(lambda: AnyRef) = tracing.tracer.traceLocation(lambda) match {
    case SourceLocation(_, clazz, _, _) => ZIO.effectTotal(LoggerFactory.getLogger(clazz))
    case NoLocation(_)                  => ZIO.effectTotal(LoggerFactory.getLogger("ZIO.defaultLogger"))
  }

  def formatMessage(msg: String): ZIO[Any, Nothing, String]

  override def trace(message: => String): ZIO[Any, Nothing, Unit] =
    for {
      l <- logger(() => message)
      _ <- ZIO.when(l.isTraceEnabled())(
            formatMessage(message)
              .flatMap(msg => ZIO.effectTotal(l.trace(msg)))
          )
    } yield ()

  override def debug(message: => String): ZIO[Any, Nothing, Unit] =
    for {
      l <- logger(() => message)
      _ <- ZIO.when(l.isDebugEnabled())(
            formatMessage(message)
              .flatMap(msg => ZIO.effectTotal(l.debug(msg)))
          )
    } yield ()

  override def info(message: => String): ZIO[Any, Nothing, Unit] =
    for {
      l <- logger(() => message)
      _ <- ZIO.when(l.isInfoEnabled())(
            formatMessage(message)
              .flatMap(msg => ZIO.effectTotal(l.info(msg)))
          )
    } yield ()

  override def warning(message: => String): ZIO[Any, Nothing, Unit] =
    for {
      l <- logger(() => message)
      _ <- ZIO.when(l.isWarnEnabled())(
            formatMessage(message)
              .flatMap(msg => ZIO.effectTotal(l.warn(msg)))
          )
    } yield ()

  override def error(message: => String): ZIO[Any, Nothing, Unit] =
    for {
      l <- logger(() => message)
      _ <- ZIO.when(l.isErrorEnabled())(
            formatMessage(message)
              .flatMap(msg => ZIO.effectTotal(l.error(msg)))
          )
    } yield ()

  override def error(message: => String, cause: Cause[Any]): ZIO[Any, Nothing, Unit] =
    for {
      l <- logger(() => message)
      _ <- ZIO.when(l.isErrorEnabled())(
            formatMessage(message)
              .flatMap(msg => ZIO.effectTotal(l.error(msg + " cause: " + cause.prettyPrint)))
          )
    } yield ()
}

object Slf4jLogger {
  trait Live extends Logging[String] {
    self =>

    def formatMessage(msg: String): ZIO[Any, Nothing, String]

    def logging: Logging.Service[Any, String] = new Slf4jLogger {
      override def formatMessage(msg: String): ZIO[Any, Nothing, String] =
        self.formatMessage(msg)
    }
  }
}
