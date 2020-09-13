package zio.logging

import java.nio.charset.Charset
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicBoolean

import izumi.reflect.Tag
import zio.console._
import zio.{ Has, Task, UIO, ULayer, URIO, ZIO, ZLayer, ZManaged, ZQueue }

/**
 * Represents log writer function that turns A into String and put in console or save to file.
 */
object LogAppender {

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
  }

  def make[R, A: Tag](
    format0: LogFormat[A],
    write0: (LogContext, => String) => URIO[R, Unit]
  ): ZLayer[R, Nothing, Appender[A]] =
    ZIO
      .access[R](env =>
        new Service[A] {

          override def write(ctx: LogContext, msg: => A): UIO[Unit] =
            write0(ctx, format0.format(ctx, msg)).provide(env)
        }
      )
      .toLayer

  def async[A: Tag](logEntryBufferSize: Int): ZLayer[Appender[A], Nothing, Appender[A]] = {
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
  }.toLayer

  def console[A: Tag](logLevel: LogLevel, format: LogFormat[A]): ZLayer[Console, Nothing, Appender[A]] =
    make[Console, A](format, (_, line) => putStrLn(line)).map(appender =>
      Has(appender.get.filter((ctx, _) => ctx.get(LogAnnotation.Level) >= logLevel))
    )

  def consoleErr[A: Tag](logLevel: LogLevel, format: LogFormat[A]): ZLayer[Console, Nothing, Appender[A]] =
    make[Console, A](
      format,
      (ctx, msg) =>
        if (ctx.get(LogAnnotation.Level) == LogLevel.Error)
          putStrLnErr(msg)
        else
          putStrLn(msg)
    ).map(appender => Has(appender.get.filter((ctx, _) => ctx.get(LogAnnotation.Level) >= logLevel)))

  def file[A: Tag](
    destination: Path,
    charset: Charset,
    autoFlushBatchSize: Int,
    bufferedIOSize: Option[Int],
    format0: LogFormat[A]
  ): ZLayer[Any, Throwable, Appender[A]] =
    ZManaged
      .fromAutoCloseable(UIO(new LogWriter(destination, charset, autoFlushBatchSize, bufferedIOSize)))
      .map(writer =>
        new Service[A] {
          private val hasWarned = new AtomicBoolean()

          override def write(ctx: LogContext, msg: => A): UIO[Unit] =
            Task(writer.writeln(format0.format(ctx, msg))).catchAll { t =>
              UIO {
                System.err.println(
                  s"Logging to file $destination failed with an exception. Further exceptions will be suppressed in order to prevent log spam."
                )
                t.printStackTrace(System.err)
              }.when(!hasWarned.getAndSet(true))
            }
        }
      )
      .toLayer

  def ignore[A: Tag]: ULayer[Appender[A]] =
    ZLayer.succeed(new Service[A] {

      override def write(ctx: LogContext, msg: => A): UIO[Unit] =
        ZIO.unit
    })
}
