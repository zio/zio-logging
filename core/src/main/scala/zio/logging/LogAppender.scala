package zio.logging

import izumi.reflect.Tag
import zio.{ UIO, URIO, URLayer, ZIO, ZLayer }

object LogAppender {
  trait Service[A] {
    def write(ctx: LogContext, msg: => A): UIO[Unit]
  }

  def make[R, A: Tag](format: LogFormat[A], write0: (LogContext, => A) => URIO[R, Unit]): URLayer[R, LogAppender[A]] =
    ZLayer.fromEffect {
      ZIO.access[R](env =>
        new Service[A] {
          override def write(ctx: LogContext, msg: => A): UIO[Unit] =
            write0(ctx, format.format(ctx, msg)).provide(env)
        }
      )
    }
}
