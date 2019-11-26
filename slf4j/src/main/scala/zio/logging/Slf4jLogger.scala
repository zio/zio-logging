package zio.logging

import org.slf4j.{LoggerFactory, MDC}
import zio.internal.Tracing
import zio.internal.stacktracer.Tracer
import zio.internal.stacktracer.ZTraceElement.{NoLocation, SourceLocation}
import zio.internal.stacktracer.impl.AkkaLineNumbersTracer
import zio.internal.tracing.TracingConfig
import zio.{Cause, FiberRef, UIO, ZIO}

class Slf4jLogger private (val map: FiberRef[Map[String, String]]) extends AbstractLogging.Service[Any, String] {

  // this is copy from PlatformLive.
  private val tracing = Tracing(Tracer.globallyCached(new AkkaLineNumbersTracer), TracingConfig.enabled)

  private def logger(lambda: AnyRef) = tracing.tracer.traceLocation(lambda) match {
    case SourceLocation(_, clazz, _, _) => ZIO.effectTotal(LoggerFactory.getLogger(clazz))
    case NoLocation(_)                  => ZIO.effectTotal(LoggerFactory.getLogger("ZIO.defaultLogger"))
  }

  private def withMDC(log: UIO[Unit]) =
    for {
      currentMap <- map.get
      _ <- if (currentMap.isEmpty) {
            log
          } else {
            ZIO
              .effectTotal(MDC.getCopyOfContextMap)
              .bracket(
                previous =>
                  ZIO.effectTotal(
                    if (previous == null)
                      MDC.clear()
                    else
                      MDC.setContextMap(previous)
                  ),
                _ =>
                  ZIO.effectTotal(
                    currentMap.foreach { case (k, v) => MDC.put(k, v) }
                  ) *> log
              )

          }
    } yield ()


  def addToContext(key: String, value: String): ZIO[Any, Nothing, Unit] =
    map.update { oldMap => oldMap.updated(key, value) }.unit

  def removeFromContext(key: String): ZIO[Any, Nothing, Unit] =
    map.update { oldMap => oldMap - key }.unit

  override def trace(message: => String): ZIO[Any, Nothing, Unit] =
    for {
      l <- logger(() => message)
      _ <- ZIO.when(l.isTraceEnabled())(
            withMDC(ZIO.effectTotal(l.trace(message)))
          )
    } yield ()

  override def debug(message: => String): ZIO[Any, Nothing, Unit] =
    for {
      l <- logger(() => message)
      _ <- ZIO.when(l.isDebugEnabled())(
            withMDC(ZIO.effectTotal(l.debug(message)))
          )
    } yield ()

  override def info(message: => String): ZIO[Any, Nothing, Unit] =
    for {
      l <- logger(() => message)
      _ <- ZIO.when(l.isInfoEnabled())(
            withMDC(ZIO.effectTotal(l.info(message)))
          )
    } yield ()

  override def warning(message: => String): ZIO[Any, Nothing, Unit] =
    for {
      l <- logger(() => message)
      _ <- ZIO.when(l.isWarnEnabled())(
            withMDC(ZIO.effectTotal(l.warn(message)))
          )
    } yield ()

  override def error(message: => String): ZIO[Any, Nothing, Unit] =
    for {
      l <- logger(() => message)
      _ <- ZIO.when(l.isErrorEnabled())(
            withMDC(ZIO.effectTotal(l.error(message)))
          )
    } yield ()

  override def error(message: => String, cause: Cause[Any]): ZIO[Any, Nothing, Unit] =
    for {
      l <- logger(() => message)
      _ <- ZIO.when(l.isErrorEnabled())(
        withMDC(ZIO.effectTotal(l.error(message + " cause: " + cause.prettyPrint)))
      )
    } yield ()
}

object Slf4jLogger {
  def apply(): UIO[Slf4jLogger] =
    FiberRef
      .make(Map.empty[String, String])
      .map(fiber => new Slf4jLogger(fiber))
}
