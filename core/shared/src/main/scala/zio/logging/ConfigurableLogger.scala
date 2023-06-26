/*
 * Copyright 2019-2023 John A. De Goes and the ZIO Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package zio.logging

import zio.logging.internal.{ ReconfigurableLogger, ReconfigurableLogger2 }
import zio.{ Cause, FiberId, FiberRef, FiberRefs, LogLevel, LogSpan, Trace, ZIO, ZLayer, ZLogger }

trait LoggerConfigurer {

  def getLoggerConfigs(): ZIO[Any, Throwable, List[LoggerConfigurer.LoggerConfig]]

  def getLoggerConfig(name: String): ZIO[Any, Throwable, Option[LoggerConfigurer.LoggerConfig]]

  def setLoggerConfig(name: String, level: LogLevel): ZIO[Any, Throwable, LoggerConfigurer.LoggerConfig]
}

object LoggerConfigurer {

  final case class LoggerConfig(name: String, level: LogLevel)

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

object ConfigurableLogger {

//  def apply[Message, Output](
//    logger: ZLogger[Message, Output],
//    loggerConfigurer: LoggerConfigurer
//  ): ConfigurableLogger[Message, Output] =
//    new ConfigurableLogger[Message, Output] {
//
//      override val configurer: LoggerConfigurer = loggerConfigurer
//
//      override def apply(
//        trace: Trace,
//        fiberId: FiberId,
//        logLevel: LogLevel,
//        message: () => Message,
//        cause: Cause[Any],
//        context: FiberRefs,
//        spans: List[LogSpan],
//        annotations: Map[String, String]
//      ): Output = logger.apply(trace, fiberId, logLevel, message, cause, context, spans, annotations)
//    }

  def make[Message, Output](
    logger: ZLogger[Message, Output],
    filterConfig: LogFilter.LogLevelByNameConfig
  ): ConfigurableLogger[Message, Option[Output]] = {

    val reconfigurableLogger = ReconfigurableLogger[Message, Option[Output], LogFilter.LogLevelByNameConfig](
      filterConfig,
      (config, _) => {
        val filter = LogFilter.logLevelByName(config)

        filter.filter(logger)
      }
    )

    new ConfigurableLogger[Message, Option[Output]] {

      override val configurer: LoggerConfigurer = Configurer(reconfigurableLogger)

      override def apply(
        trace: Trace,
        fiberId: FiberId,
        logLevel: LogLevel,
        message: () => Message,
        cause: Cause[Any],
        context: FiberRefs,
        spans: List[LogSpan],
        annotations: Map[String, String]
      ): Option[Output] =
        reconfigurableLogger.apply(trace, fiberId, logLevel, message, cause, context, spans, annotations)
    }
  }

  private case class Configurer(logger: ReconfigurableLogger[_, _, LogFilter.LogLevelByNameConfig])
      extends LoggerConfigurer {

    private val rootName = "root"

    override def getLoggerConfigs(): ZIO[Any, Throwable, List[LoggerConfigurer.LoggerConfig]] =
      ZIO.attempt {
        val currentConfig = logger.config

        LoggerConfigurer.LoggerConfig(rootName, currentConfig.rootLevel) :: currentConfig.mappings.map { case (n, l) =>
          LoggerConfigurer.LoggerConfig(n, l)
        }.toList
      }

    override def getLoggerConfig(name: String): ZIO[Any, Throwable, Option[LoggerConfigurer.LoggerConfig]] =
      ZIO.attempt {
        val currentConfig = logger.config

        if (name == rootName) {
          Some(LoggerConfigurer.LoggerConfig(rootName, currentConfig.rootLevel))
        } else {
          currentConfig.mappings.collectFirst {
            case (n, l) if n == name => LoggerConfigurer.LoggerConfig(n, l)
          }
        }
      }

    override def setLoggerConfig(name: String, level: LogLevel): ZIO[Any, Throwable, LoggerConfigurer.LoggerConfig] =
      ZIO.attempt {
        val currentConfig = logger.config

        val newConfig = if (name == rootName) {
          currentConfig.withRootLevel(level)
        } else {
          currentConfig.withMapping(name, level)
        }

        logger.reconfigureIfChanged(newConfig)

        LoggerConfigurer.LoggerConfig(name, level)
      }
  }

  def make2[Message, Output](
    logger: ZLogger[Message, Output],
    filterConfig: LogFilter.LogLevelByNameConfig
  ): ConfigurableLogger[Message, Option[Output]] = {

    val initialLogger = LogFilter.logLevelByName(filterConfig).filter(logger)

    val reconfigurableLogger = ReconfigurableLogger2[Message, Option[Output], LogFilter.LogLevelByNameConfig](
      filterConfig,
      initialLogger
    )

    new ConfigurableLogger[Message, Option[Output]] {

      override val configurer: LoggerConfigurer =
        Configurer2(filterConfig => LogFilter.logLevelByName(filterConfig).filter(logger), reconfigurableLogger)

      override def apply(
        trace: Trace,
        fiberId: FiberId,
        logLevel: LogLevel,
        message: () => Message,
        cause: Cause[Any],
        context: FiberRefs,
        spans: List[LogSpan],
        annotations: Map[String, String]
      ): Option[Output] =
        reconfigurableLogger.apply(trace, fiberId, logLevel, message, cause, context, spans, annotations)
    }
  }

  private case class Configurer2[M, O](
    makeLogger: LogFilter.LogLevelByNameConfig => ZLogger[M, O],
    logger: ReconfigurableLogger2[M, Option[O], LogFilter.LogLevelByNameConfig]
  ) extends LoggerConfigurer {
    import zio.prelude._

    private val rootName = "root"

    override def getLoggerConfigs(): ZIO[Any, Throwable, List[LoggerConfigurer.LoggerConfig]] =
      ZIO.attempt {
        val currentConfig = logger.get._1

        LoggerConfigurer.LoggerConfig(rootName, currentConfig.rootLevel) :: currentConfig.mappings.map { case (n, l) =>
          LoggerConfigurer.LoggerConfig(n, l)
        }.toList
      }

    override def getLoggerConfig(name: String): ZIO[Any, Throwable, Option[LoggerConfigurer.LoggerConfig]] =
      ZIO.attempt {
        val currentConfig = logger.get._1

        if (name == rootName) {
          Some(LoggerConfigurer.LoggerConfig(rootName, currentConfig.rootLevel))
        } else {
          currentConfig.mappings.collectFirst {
            case (n, l) if n == name => LoggerConfigurer.LoggerConfig(n, l)
          }
        }
      }

    override def setLoggerConfig(name: String, level: LogLevel): ZIO[Any, Throwable, LoggerConfigurer.LoggerConfig] =
      ZIO.attempt {
        val currentConfig = logger.get._1

        val newConfig = if (name == rootName) {
          currentConfig.withRootLevel(level)
        } else {
          currentConfig.withMapping(name, level)
        }

        if (currentConfig !== newConfig) {
          val newLogger = makeLogger(newConfig)
          logger.set(newConfig, newLogger)
        }

        LoggerConfigurer.LoggerConfig(name, level)
      }
  }

}
