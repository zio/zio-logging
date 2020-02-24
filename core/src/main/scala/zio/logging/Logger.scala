package zio.logging

import zio.{ FiberRef, UIO, URIO, ZIO }

/**
 * A logger of strings.
 */
trait Logger extends LoggerLike[String]
object Logger {

  def makeWithName[R](name: String)(logger: (LogContext, => String) => URIO[R, Unit]): URIO[R, Logger] =
    make((context, line) => logger(context.annotate(LogAnnotation.Name, name :: Nil), line))

  /**
   * Constructs a logger provided the specified sink.
   */
  def make[R](logger: (LogContext, => String) => URIO[R, Unit]): URIO[R, Logger] =
    ZIO
      .environment[R]
      .flatMap(env =>
        FiberRef
          .make(LogContext.empty)
          .map { ref =>
            new Logger {
              def locally[R1, E, A](f: LogContext => LogContext)(zio: ZIO[R1, E, A]): ZIO[R1, E, A] =
                ref.get.flatMap(context => ref.locally(f(context))(zio))

              def log(line: => String): UIO[Unit] = ref.get.flatMap(context => logger(context, line).provide(env))

              def logContext: UIO[LogContext] = ref.get
            }
          }
      )

}
