package zio.logging

import zio.console._
import zio.{ Has, Tag, Task, UIO, ULayer, URIO, ZIO, ZLayer, ZManaged, ZQueue, ZRef }

import java.nio.charset.Charset
import java.nio.file.Path

/**
 * Represents log writer function that turns A into String and put in console or save to file.
 */
object LogAppender extends PlatformSpecificLogAppenderModifiers {

  trait Service[A] { self =>

    def write(ctx: LogContext, msg: => A): UIO[Unit]

    final def filter(fn: (LogContext, => A) => Boolean): Service[A] =
      new Service[A] {
        override def write(ctx: LogContext, msg: => A): zio.UIO[Unit] =
          if (fn(ctx, msg))
            self.write(ctx, msg)
          else
            ZIO.unit
      }

    final def filterM(fn: (LogContext, => A) => UIO[Boolean]): Service[A] =
      new Service[A] {
        override def write(ctx: LogContext, msg: => A): zio.UIO[Unit] =
          self.write(ctx, msg).whenM(fn(ctx, msg))
      }
  }

  def make[R, A](
    format0: LogFormat[A],
    write0: (LogContext, => String) => URIO[R, Unit]
  )(implicit tag: Tag[LogAppender.Service[A]]): ZLayer[R, Nothing, Appender[A]] =
    ZIO
      .access[R](env =>
        new Service[A] {

          override def write(ctx: LogContext, msg: => A): UIO[Unit] =
            write0(ctx, format0.format(ctx, msg)).provide(env)
        }
      )
      .toLayer[LogAppender.Service[A]]

  def async[A](
    logEntryBufferSize: Int
  )(implicit tag: Tag[LogAppender.Service[A]]): ZLayer[Appender[A], Nothing, Appender[A]] = {
    case class LogEntry(ctx: LogContext, line: () => A)
    ZManaged.accessManaged[Appender[A]](env =>
      ZQueue
        .bounded[LogEntry](logEntryBufferSize)
        .tap(queue => queue.take.flatMap(entry => env.get.write(entry.ctx, entry.line())).forever.forkDaemon)
        .toManaged(_.shutdown)
        .map(queue =>
          new Service[A] {

            override def write(ctx: LogContext, msg: => A): UIO[Unit] =
              queue.offer(LogEntry(ctx, () => msg)).unit
          }
        )
    )
  }.toLayer[LogAppender.Service[A]]

  def console[A](logLevel: LogLevel, format: LogFormat[A])(implicit
    tag: Tag[LogAppender.Service[A]]
  ): ZLayer[Console, Nothing, Appender[A]] =
    make[Console, A](format, (_, line) => putStrLn(line)).map(appender =>
      Has(appender.get.filter((ctx, _) => ctx.get(LogAnnotation.Level) >= logLevel))
    )

  def consoleErr[A](logLevel: LogLevel, format: LogFormat[A])(implicit
    tag: Tag[LogAppender.Service[A]]
  ): ZLayer[Console, Nothing, Appender[A]] =
    make[Console, A](
      format,
      (ctx, msg) =>
        if (ctx.get(LogAnnotation.Level) == LogLevel.Error)
          putStrLnErr(msg)
        else
          putStrLn(msg)
    ).map(appender => Has(appender.get.filter((ctx, _) => ctx.get(LogAnnotation.Level) >= logLevel)))

  def file[A](
    destination: Path,
    charset: Charset,
    autoFlushBatchSize: Int,
    bufferedIOSize: Option[Int],
    format0: LogFormat[A]
  )(implicit tag: Tag[LogAppender.Service[A]]): ZLayer[Any, Throwable, Appender[A]] =
    ZManaged
      .fromAutoCloseable(UIO(new LogWriter(destination, charset, autoFlushBatchSize, bufferedIOSize)))
      .zip(ZRef.makeManaged(false))
      .map { case (writer, hasWarned) =>
        new Service[A] {
          override def write(ctx: LogContext, msg: => A): UIO[Unit] =
            Task(writer.writeln(format0.format(ctx, msg))).catchAll { t =>
              UIO {
                System.err.println(
                  s"Logging to file $destination failed with an exception. Further exceptions will be suppressed in order to prevent log spam."
                )
                t.printStackTrace(System.err)
              }.unlessM(hasWarned.getAndSet(true))
            }
        }
      }
      .toLayer[LogAppender.Service[A]]

  def ignore[A](implicit tag: Tag[LogAppender.Service[A]]): ULayer[Appender[A]] =
    ZLayer.succeed[LogAppender.Service[A]](new Service[A] {

      override def write(ctx: LogContext, msg: => A): UIO[Unit] =
        ZIO.unit
    })

  implicit class AppenderLayerOps[A, RIn, E](layer: ZLayer[RIn, E, Appender[A]])(implicit
    tag: Tag[LogAppender.Service[A]]
  ) {
    def withFilter(filter: (LogContext, => A) => Boolean): ZLayer[RIn, E, Appender[A]]       =
      layer
        .map(a => Has(a.get.filter(filter)))
    def withFilterM(filter: (LogContext, => A) => UIO[Boolean]): ZLayer[RIn, E, Appender[A]] =
      layer.map(a => Has(a.get.filterM(filter)))
  }
}
