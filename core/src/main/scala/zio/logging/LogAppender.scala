package zio.logging

import java.io.FileWriter
import java.nio.file.{ Files, Path }

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

  def file[A: Tag](destination: Path, format0: LogFormat[A]): ZLayer[Any, Throwable, Appender[A]] =
    ZManaged
      .makeEffect(Files.newBufferedWriter(destination))(_.close())
      .map(writer =>
        new Service[A] {
          override def write(ctx: LogContext, msg: => A): UIO[Unit] =
            ZIO.effectTotal {
              writer.write(s"${format0.format(ctx, msg)}${System.lineSeparator}")
            }
        }
      )
      .toLayer

  def fileAsync[A: Tag](
    destination: Path,
    format0: LogFormat[A],
    autoFlushBatchSize: Int
  ): ZLayer[Any, Throwable, Appender[A]] =
    ZManaged.make {
      for {
        writer <- Task(new FileWriter(destination.toFile))
        queue  <- ZQueue.unbounded[(LogContext, A)]
        _      <- (for {
                      messages <- queue.takeBetween(1, autoFlushBatchSize)
                      _        <- Task {
                                    messages.foreach {
                                      case (ctx, msg) =>
                                        writer.write(s"${format0.format(ctx, msg)}${System.lineSeparator}")
                                    }

                                    writer.flush()
                                  }
                    } yield ()).forever.forkDaemon
      } yield (writer, queue)
    } { case (writer, queue) => Task(writer.close()).ignore *> queue.shutdown }.map {
      case (_, queue) =>
        new Service[A] {
          override def write(ctx: LogContext, msg: => A): UIO[Unit] =
            queue.offer((ctx, msg)).unit
        }
    }.toLayer

  def ignore[A: Tag]: ULayer[Appender[A]] =
    ZLayer.succeed(new Service[A] {

      override def write(ctx: LogContext, msg: => A): UIO[Unit] =
        ZIO.unit
    })
}
