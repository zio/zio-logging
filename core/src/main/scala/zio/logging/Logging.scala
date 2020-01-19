package zio.logging

import zio.clock.{ currentDateTime, Clock }
import zio.console.{ putStrLn, Console }
import zio.logging.Logger.classNameForLambda
import zio.{ UIO, URIO, ZIO }

trait Logging[-R] {

  def logger: Logger[R]

}

object Logging {

  def make[R](logLevel: LogLevel, logger: (LogContext, => String) => URIO[R, Unit]) =
    Logger
      .make(logLevel, logger)
      .map(l =>
        new Logging[R] {
          override def logger: Logger[R] = l
        }
      )

  val ignore: UIO[Logging[Any]] =
    make(LogLevel.Off, (_, _) => ZIO.unit)

  def console(
    logLevel: LogLevel,
    format: (LogContext, => String) => String
  ): URIO[Console with Clock, Logging[Console with Clock]] =
    make(
      logLevel,
      (context, line) =>
        for {
          date  <- currentDateTime
          level = context.get(LogAnnotation.Level)
          loggerName = context.get(LogAnnotation.Name) match {
            case Nil   => classNameForLambda(line).getOrElse("ZIO.defaultLogger")
            case names => LogAnnotation.Name.render(names)
          }
          _ <- putStrLn(date.toString + " " + level.render + " " + loggerName + " " + format(context, line))
        } yield ()
    )

  def log(line: => String): ZIO[Logging[Any], Nothing, Unit] =
    ZIO.accessM[Logging[Any]](_.logger.log(line))

  def log(level: LogLevel)(line: => String): ZIO[Logging[Any], Nothing, Unit] =
    ZIO.accessM[Logging[Any]](_.logger.log(level)(line))

  def locallyAnnotate[A, R <: Logging[Any], E, A1](annotation: LogAnnotation[A], value: A)(
    zio: ZIO[R, E, A1]
  ): ZIO[Logging[Any] with R, E, A1] =
    ZIO.accessM[Logging[Any] with R](_.logger.locallyAnnotate[A, R, E, A1](annotation, value)(zio))

  def contramap[A1](f: A1 => String): URIO[Logging[Any], LoggerLike[A1, Any]] =
    ZIO.access[Logging[Any]](_.logger.contramap(f))

  def derive(f: LogContext => LogContext): URIO[Logging[Any], LoggerLike[String, Any]] =
    ZIO.access[Logging[Any]](_.logger.derive(f))

  def locally[R1 <: Logging[Any], E, A1](f: LogContext => LogContext)(zio: ZIO[R1, E, A1]): ZIO[R1, E, A1] =
    ZIO.accessM[R1](_.logger.locally(f)(zio))

  def logger: URIO[Logging[Any], Logger[Any]] =
    ZIO.access[Logging[Any]](_.logger)

  def logContext: URIO[Logging[Any], LogContext] =
    ZIO.accessM[Logging[Any]](_.logger.logContext)

  def named(name: String): URIO[Logging[Any], LoggerLike[String, Any]] =
    ZIO.access[Logging[Any]](_.logger.named(name))

}
