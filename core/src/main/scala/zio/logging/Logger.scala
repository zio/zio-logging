package zio.logging

import zio.{FiberRef, UIO, ZIO}

/**
 * A logger of strings.
 */
trait Logger extends LoggerLike[String]
object Logger {
  /**
   * Constructs a logger provided the specified sink.
   */
  def make(logger: (LogContext, => String) => UIO[Unit]): UIO[Logger] =
    FiberRef.make(LogContext.empty).map { ref =>
      new Logger {
        def locally[R, E, A](f: LogContext => LogContext)(zio: ZIO[R, E, A]): ZIO[R, E, A] =
          ref.get.flatMap(context => ref.locally(f(context))(zio))

        def log(line: => String): UIO[Unit] = ref.get.flatMap(context => logger(context, line))

        def logContext: UIO[LogContext] = ref.get
      }
    }
}
