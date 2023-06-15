package zio.logging

import zio.{ FiberRef, LogLevel, ZIO, ZLayer, ZLogger }

trait LoggerConfigurer {

  def getLoggerConfigs(): ZIO[Any, Throwable, List[LoggerConfigurer.LoggerConfig]]

  def getLoggerConfig(name: String): ZIO[Any, Throwable, Option[LoggerConfigurer.LoggerConfig]]

  def setLoggerConfig(name: String, logLevel: LogLevel): ZIO[Any, Throwable, LoggerConfigurer.LoggerConfig]
}

object LoggerConfigurer {

  final case class LoggerConfig(name: String, logLevel: LogLevel)

  def getLoggerConfigs(): ZIO[LoggerConfigurer, Throwable, List[LoggerConfigurer.LoggerConfig]] =
    ZIO.serviceWithZIO[LoggerConfigurer](_.getLoggerConfigs())

  def getLoggerConfig(name: String): ZIO[LoggerConfigurer, Throwable, Option[LoggerConfigurer.LoggerConfig]] =
    ZIO.serviceWithZIO[LoggerConfigurer](_.getLoggerConfig(name))

  def setLoggerConfig(
    name: String,
    logLevel: LogLevel
  ): ZIO[LoggerConfigurer, Throwable, LoggerConfigurer.LoggerConfig] =
    ZIO.serviceWithZIO[LoggerConfigurer](_.setLoggerConfig(name, logLevel))

  val layer: ZLayer[Any, Throwable, LoggerConfigurer] =
    ZLayer.fromZIO {
      for {
        fiberRefs <- ZIO.getFiberRefs

        loggerService <- ZIO.attempt {
                           val loggers = fiberRefs.getOrDefault(FiberRef.currentLoggers)
                           loggers.collectFirst { case logger: ConfigurableLogger[_, _] =>
                             logger.configurer
                           }.getOrElse(throw new RuntimeException("LoggerConfigurer not found"))
                         }
      } yield loggerService
    }
}

trait ConfigurableLogger[-Message, +Output] extends ZLogger[Message, Output] {

  def configurer: LoggerConfigurer
}
