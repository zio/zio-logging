package zio.logging

import zio.{ Has, UIO, ULayer, URIO, ZIO, ZLayer, ZManaged }
import zio.console._
import izumi.reflect.Tag
import java.nio.file.Files
import java.nio.file.Paths

/**
 * Represets log writer function that turns A into String and put in console or save to file.
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

  def file[A: Tag](filename: String, format0: LogFormat[A]): ZLayer[Any, Throwable, Appender[A]] =
    ZManaged.makeEffect {
      val path = Paths.get(filename)
      Files.newBufferedWriter(path)
    }(_.close())
      .map(writer =>
        new Service[A] {

          override def write(ctx: LogContext, msg: => A): UIO[Unit] =
            ZIO.effectTotal(writer.write(format0.format(ctx, msg)))
        }
      )
      .toLayer

  def ignore[A: Tag]: ULayer[Appender[A]] =
    ZLayer.succeed(new Service[A] {

      override def write(ctx: LogContext, msg: => A): UIO[Unit] =
        ZIO.unit
    })
}
