package zio

import zio.Clock._
import zio.stream.ZStream
import zio.{
  Cause,
  Clock,
  Console,
  FiberRef,
  Layer,
  RuntimeConfigAspect,
  URIO,
  URLayer,
  ZEnvironment,
  ZIO,
  ZLayer,
  ZManaged,
  ZTraceElement
}

import java.nio.charset.{ Charset, StandardCharsets }
import java.nio.file.Path

package object logging {

  val logAnnotation: FiberRef.Runtime[Map[String, String]] =
    FiberRef.unsafeMake(Map.empty, identity, (old, newV) => old ++ newV)

  /**
   * Add annotations to log context
   *
   * example of usage:
   * {{{
   *  ZIO.log("my message") @@ annotate("requestId" -> UUID.random.toString)
   * }}}
   */
  def annotate(annotations: (String, String)*): ZIOAspect[Nothing, Any, Nothing, Any, Nothing, Any] =
    new ZIOAspect[Nothing, Any, Nothing, Any, Nothing, Any] {
      def apply[R, E, A](zio: ZIO[R, E, A])(implicit trace: ZTraceElement): ZIO[R, E, A] =
        logAnnotation.get.flatMap(old => logAnnotation.locally(old ++ annotations.toMap)(zio))
    }

  def console(
    logLevel: LogLevel = LogLevel.Info,
    format: LogFormat = LogFormat.colored
  ): RuntimeConfigAspect =
    RuntimeConfigAspect.addLogger(
      ???
    )

  def consoleErr(
    logLevel: LogLevel = LogLevel.Info,
    format: LogFormat = LogFormat.default
  ): RuntimeConfigAspect =
    ???

  def file(
    destination: Path,
    charset: Charset = StandardCharsets.UTF_8,
    autoFlushBatchSize: Int = 1,
    bufferedIOSize: Option[Int] = None,
    logLevel: LogLevel = LogLevel.Info,
    format: LogFormat = LogFormat.default
  ): RuntimeConfigAspect =
    RuntimeConfigAspect.addLogger(
      ???
    )

  def fileAsync(
    destination: Path,
    charset: Charset = StandardCharsets.UTF_8,
    autoFlushBatchSize: Int = 32,
    bufferedIOSize: Option[Int] = Some(8192),
    logLevel: LogLevel = LogLevel.Info,
    format: LogFormat = LogFormat.default
  ): RuntimeConfigAspect =
    RuntimeConfigAspect.addLogger(
      ???
    )
}
