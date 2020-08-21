package zio.logging

import zio.{ UIO, URIO, ZIO }
import zio.console._
import zio.ZLayer
import izumi.reflect.Tag
import zio.ZManaged
import java.nio.file.Files
import java.nio.file.Paths
import zio.Has

/**
 * Represets log writer function that turns A into String and put in console or save to file.
 */
object LogAppender {

  trait Service[A] { self =>

    def write(ctx: LogContext, msg: => A): UIO[Unit]

    def filter(fn: (LogContext, => A) => Boolean): Service[A] =
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
  ): ZLayer[R, Nothing, LogAppender[A]] =
    ZIO
      .access[R](env =>
        new Service[A] {

          override def write(ctx: LogContext, msg: => A): UIO[Unit] =
            write0(ctx, format0.format(ctx, msg)).provide(env)
        }
      )
      .toLayer

  def console[A: Tag](logLevel: LogLevel, format: LogFormat[A]): ZLayer[Console, Nothing, LogAppender[A]] =
    make[Console, A](format, (_, line) => putStrLn(line)).map(appender =>
      Has(appender.get.filter((ctx, _) => ctx.get(LogAnnotation.Level) >= logLevel))
    )

  def consoleErr(format: LogFormat[String]) =
    make[Console, String](
      format,
      (ctx, msg) =>
        if (ctx.get(LogAnnotation.Level) == LogLevel.Error)
          putStrLnErr(format.format(ctx, msg))
        else
          putStrLn(format.format(ctx, msg))
    )

  def file[A: Tag](filename: String, format0: LogFormat[A]) =
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

  def ignore[A: Tag] =
    ZLayer.succeed(new Service[A] {

      override def write(ctx: LogContext, msg: => A): UIO[Unit] =
        ZIO.unit
    }.filter((_, _) => false))
}
