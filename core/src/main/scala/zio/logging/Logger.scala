package zio.logging
import zio.{ Cause, UIO, URIO, ZIO }

trait Logger[-A] { self =>

  /**
   * Produces a new logger by adapting a different input type to the input
   * type of this logger.
   */
  final def contramap[A1](f: A1 => A): Logger[A1] =
    new Logger[A1] {
      def locally[R1, E, A2](f: LogContext => LogContext)(zio: ZIO[R1, E, A2]): ZIO[R1, E, A2] =
        self.locally(f)(zio)

      def log(line: => A1): UIO[Unit] = self.log(f(line))

      def logContext: UIO[LogContext] = self.logContext
    }

  /**
   * Logs the specified element at the debug level.
   */
  def debug(line: => A): UIO[Unit] =
    self.log(LogLevel.Debug)(line)

  /**
   * Logs the specified element at the error level.
   */
  def error(line: => A): UIO[Unit] =
    self.log(LogLevel.Error)(line)

  /**
   * Logs the specified element at the error level with cause.
   */
  def error(line: => A, cause: Cause[Any]) =
    self.locally(LogAnnotation.Cause(Some(cause))) {
      self.log(LogLevel.Error)(line)
    }

  /**
   * Derives a new logger from this one, by applying the specified decorator
   * to the logger context.
   */
  def derive(f: LogContext => LogContext): Logger[A] =
    new Logger[A] {
      def locally[R1, E, A1](f: LogContext => LogContext)(zio: ZIO[R1, E, A1]): ZIO[R1, E, A1] =
        self.locally(f)(zio)

      def log(line: => A): UIO[Unit] = locally(f)(self.log(line))

      def logContext: UIO[LogContext] = self.logContext
    }

  /**
   * Logs the specified element at the info level
   */
  def info(line: => A): UIO[Unit] =
    self.log(LogLevel.Info)(line)

  /**
   * Modifies the log context in the scope of the specified effect.
   */
  def locally[R1, E, A1](f: LogContext => LogContext)(zio: ZIO[R1, E, A1]): ZIO[R1, E, A1]

  /**
   * Modifies the log context with effect in the scope of the specified effect.
   */
  def locallyM[R1, E, A1](f: LogContext => URIO[R1, LogContext])(zio: ZIO[R1, E, A1]): ZIO[R1, E, A1] =
    logContext.flatMap(ctx => f(ctx)).flatMap(ctx => locally(_ => ctx)(zio))

  /**
   * Modifies the annotate in the scope of the specified effect.
   */
  final def locallyAnnotate[B, R, E, A1](annotation: LogAnnotation[B], value: B)(zio: ZIO[R, E, A1]): ZIO[R, E, A1] =
    locally(_.annotate(annotation, value))(zio)

  /**
   * Logs the specified element using an inherited log level.
   */
  def log(line: => A): UIO[Unit]

  /**
   * Retrieves the log context.
   */
  def logContext: UIO[LogContext]

  /**
   * Logs the specified element at the specified level. Implementations may
   * override this for greater efficiency.
   */
  def log(level: LogLevel)(line: => A): UIO[Unit] =
    locally(_.annotate(LogAnnotation.Level, level))(log(line))

  /**
   * Produces a named logger.
   */
  def named(name: String): Logger[A] =
    derive(_.annotate(LogAnnotation.Name, name :: Nil))

  /**
   * Logs the specified element at the error level with exception.
   */
  def throwable(line: => A, t: Throwable) =
    self.locally(LogAnnotation.Throwable(Some(t))) {
      self.error(line)
    }

  /**
   * Logs the specified element at the trace level.
   */
  def trace(line: => A): UIO[Unit] =
    self.log(LogLevel.Trace)(line)

  /**
   * Logs the specified element at the warn level.
   */
  def warn(line: => A) =
    self.log(LogLevel.Warn)(line)
}
