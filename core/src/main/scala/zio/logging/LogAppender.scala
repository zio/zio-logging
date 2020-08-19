package zio.logging

import zio.{UIO, URIO, ZIO}
import zio.console._

trait LogAppender[R, A] { self =>
  
  def write(ctx: LogContext, msg: => A): URIO[R, Unit]
  
  final def filter(fn: (LogContext, => A) => Boolean): LogAppender[R, A] = new LogAppender[R, A] {
    def write(ctx: LogContext, msg: => A): zio.URIO[R,Unit] = 
      if(fn(ctx, msg)) {
        self.write(ctx, msg)
      } else {
        ZIO.unit
      }
  }
}

object LogAppender {

  def make[R, A](format: LogFormat[A], write0: (LogContext, => A) => URIO[R, Unit]): LogAppender[R, A] =
        new LogAppender[R, A] {
          override def write(ctx: LogContext, msg: => A): URIO[R, Unit] =
            write0(ctx, format.format(ctx, msg))
        }


  def console(format: LogFormat[String]): LogAppender[Console, String] =
    new LogAppender[Console, String] {
      def write(ctx: LogContext, msg: => String): zio.URIO[Console,Unit] = 
        putStrLn(format.format(ctx, msg))
    }

  def consoleErr(format: LogFormat[String]): LogAppender[Console, String] =
    new LogAppender[Console, String] {
      def write(ctx: LogContext, msg: => String): zio.URIO[Console,Unit] =
        if(ctx.get(LogAnnotation.Level) == LogLevel.Error) {
          putStrLnErr(format.format(ctx, msg))
        } else {
          putStrLn(format.format(ctx, msg))
        }
    }


  def ignore[A] = new LogAppender[Any, A] {
    override def write(ctx: LogContext, msg: => A): UIO[Unit] =
      ZIO.unit
  }



//  empty
//  many
//  file appender
//    async appender
//    secure logging with filter
}
