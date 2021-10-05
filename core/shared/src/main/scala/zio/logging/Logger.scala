package zio.logging

import zio.stream.ZStream
import zio.{ Cause, FiberRef, UIO, URIO, ZIO, ZManaged }

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
   * Evaluates the specified element based on the LogLevel set and logs at the debug level
   */
  def debugM[R, E](line: ZIO[R, E, A]): ZIO[R, E, Unit] = line >>= (debug(_))

  /**
   * Logs the specified element at the error level.
   */
  def error(line: => A): UIO[Unit] =
    self.log(LogLevel.Error)(line)

  /**
   * Evaluates the specified element based on the LogLevel set and logs at the error level
   */
  def errorM[R, E](line: ZIO[R, E, A]): ZIO[R, E, Unit] = line >>= (error(_))

  /**
   * Logs the specified element at the error level with cause.
   */
  def error(line: => A, cause: Cause[Any]): UIO[Unit] =
    self.locally(LogAnnotation.Cause(Some(cause))) {
      self.log(LogLevel.Error)(line)
    }

  /**
   * Evaluates the specified element based on the LogLevel set and logs at the error level
   */
  def errorM[R, E](line: ZIO[R, E, A], cause: Cause[Any]): ZIO[R, E, Unit] = line.flatMap(l => error(l, cause))

  /**
   * Derives a new logger from this one, by applying the specified decorator
   * to the logger context.
   */
  def derive(f: LogContext => LogContext): Logger[A] =
    new Logger[A] {
      def locally[R1, E, A1](f: LogContext => LogContext)(zio: ZIO[R1, E, A1]): ZIO[R1, E, A1] =
        self.locally(f)(zio)

      def log(line: => A): UIO[Unit] =
        locally(ctx => f(LogContext.empty).merge(ctx))(self.log(line))

      def logContext: UIO[LogContext] =
        self.logContext
    }

  /**
   * Derives a new logger from this one, by applying the specified decorator
   * to the logger context.
   */
  def deriveM[R](f: LogContext => ZIO[R, Nothing, LogContext]): ZIO[R, Nothing, Logger[A]] =
    ZIO.access[R] { env =>
      new Logger[A] {
        def locally[R1, E, A1](f: LogContext => LogContext)(zio: ZIO[R1, E, A1]): ZIO[R1, E, A1] =
          self.locally(f)(zio)

        def log(line: => A): UIO[Unit] =
          locallyM(ctx => f(LogContext.empty).map(_.merge(ctx)).provide(env))(self.log(line))

        def logContext: UIO[LogContext] =
          self.logContext
      }
    }

  /**
   * Logs the specified element at the info level
   */
  def info(line: => A): UIO[Unit] =
    self.log(LogLevel.Info)(line)

  /**
   * Evaluates the specified element based on the LogLevel set and logs at the info level
   */
  def infoM[R, E](line: ZIO[R, E, A]): ZIO[R, E, Unit] = line >>= (info(_))

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
   * Modify log context in scope of Managed operation.
   */
  def locallyManaged[R1, E, A1](f: LogContext => LogContext)(managed: ZManaged[R1, E, A1]): ZManaged[R1, E, A1] =
    ZManaged.makeReserve(managed.reserve.map(r => r.copy(locally(f)(r.acquire), exit => locally(f)(r.release(exit)))))

  /**
   * Modify log context in scope of ZStream.
   */
  def locallyZStream[R1, E, A1](f: LogContext => LogContext)(stream: ZStream[R1, E, A1]): ZStream[R1, E, A1] =
    ZStream(stream.process.map(p => locally(f)(p)))

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
  def throwable(line: => A, t: Throwable): UIO[Unit] =
    self.locally(LogAnnotation.Throwable(Some(t))) {
      self.error(line)
    }

  /**
   * Logs the specified element at the trace level.
   */
  def trace(line: => A): UIO[Unit] =
    self.log(LogLevel.Trace)(line)

  /**
   * Evaluates the specified element based on the LogLevel set and logs at the trace level
   */
  def traceM[R, E](line: ZIO[R, E, A]): ZIO[R, E, Unit] = line >>= (trace(_))

  /**
   * Logs the specified element at the warn level.
   */
  def warn(line: => A): UIO[Unit] =
    self.log(LogLevel.Warn)(line)

  /**
   * Evaluates the specified element based on the LogLevel set and logs at the warn level
   */
  def warnM[R, E](line: ZIO[R, E, A]): ZIO[R, E, Unit] = line >>= (warn(_))

  /**
   * Logs the specified element at the warn level with cause.
   */
  def warn(line: => A, cause: Cause[Any]): UIO[Unit] =
    self.locally(LogAnnotation.Cause(Some(cause))) {
      self.log(LogLevel.Warn)(line)
    }

  /**
   * Evaluates the specified element based on the LogLevel set and logs at the warn level with cause
   */
  def warnM[R, E](line: ZIO[R, E, A], cause: Cause[Any]): ZIO[R, E, Unit] = line >>= (warn(_, cause))
}

object Logger {
  final case class LoggerWithFormat[R, A](contextRef: FiberRef[LogContext], appender: LogAppender.Service[A])
      extends Logger[A] {

    /**
     * Modifies the log context in the scope of the specified effect.
     */
    override def locally[R1, E, A1](f: LogContext => LogContext)(zio: ZIO[R1, E, A1]): ZIO[R1, E, A1] =
      contextRef.get.flatMap(context => contextRef.locally(f(context))(zio))

    /**
     * Logs the specified element using an inherited log level.
     */
    override def log(line: => A): UIO[Unit] =
      contextRef.get.flatMap(context => appender.write(context, line))

    /**
     * Retrieves the log context.
     */
    override def logContext: UIO[LogContext] = contextRef.get
  }
}
