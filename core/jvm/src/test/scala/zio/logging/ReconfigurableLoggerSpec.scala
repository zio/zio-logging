package zio.logging

import zio.test._
import zio.{
  Cause,
  Config,
  ConfigProvider,
  FiberId,
  FiberRefs,
  LogLevel,
  LogSpan,
  Queue,
  Runtime,
  Trace,
  UIO,
  ZIO,
  ZLayer,
  ZLogger
}

import java.util.concurrent.atomic.AtomicReference

object ReconfigurableLoggerSpec extends ZIOSpecDefault {

  trait ReconfigurableLogger[-Message, +Output, Config] extends ZLogger[Message, Output] {
    def reconfigure(config: Config): Unit

    def reconfigureIfChanged(config: Config): Boolean
  }

  object ReconfigurableLogger {
    def apply[M, O, C](
      config: C,
      makeLogger: C => ZLogger[M, O]
    ): ReconfigurableLogger[M, O, C] =
      new ReconfigurableLogger[M, O, C] {
        private val configureLogger: AtomicReference[(C, ZLogger[M, O])] = {
          val logger = makeLogger(config)
          new AtomicReference[(C, ZLogger[M, O])]((config, logger))
        }

        override def reconfigureIfChanged(config: C): Boolean =
          if (configureLogger.get()._1 != config) {
            reconfigure(config)
            true
          } else false

        override def reconfigure(config: C): Unit = {
          val logger = makeLogger(config)
          configureLogger.set((config, logger))
        }

        override def apply(
          trace: Trace,
          fiberId: FiberId,
          logLevel: LogLevel,
          message: () => M,
          cause: Cause[Any],
          context: FiberRefs,
          spans: List[LogSpan],
          annotations: Map[String, String]
        ): O =
          configureLogger.get()._2.apply(trace, fiberId, logLevel, message, cause, context, spans, annotations)
      }

  }

  def configuredLogger(queue: zio.Queue[String], configPath: String = "logger"): ZLayer[Any, Config.Error, Unit] =
    ZLayer.scoped {
      for {
        config <- ZIO.config(ConsoleLoggerConfig.config.nested(configPath))

        logger = ReconfigurableLogger[String, Any, ConsoleLoggerConfig](
                   config,
                   config =>
                     config.format.toLogger.map { line =>
                       zio.Unsafe.unsafe { implicit u =>
                         Runtime.default.unsafe.run(queue.offer(line))
                       }
                     }.filter(config.filter)
                 )

        _ <- ZIO.withLoggerScoped(logger)
      } yield ()
    }

  val spec: Spec[Environment, Any] = suite("ReconfigurableLogger")(
    test("log") {

      val logFormat = "%message"

      val configProvider: ConfigProvider = ConfigProvider.fromMap(
        Map(
          "logger/format"                                              -> logFormat,
          "logger/filter/rootLevel"                                    -> LogLevel.Info.label,
          "logger/filter/mappings/zio.logging.example.LivePingService" -> LogLevel.Debug.label
        ),
        "/"
      )

      Queue.unbounded[String].flatMap { queue =>
        (for {
          _         <- ZIO.logInfo("info")
          elements1 <- queue.takeAll
          _         <- ZIO.logInfo("debug")
          elements2 <- queue.takeAll
        } yield assertTrue(elements1.nonEmpty && elements2.nonEmpty))
          .provide(Runtime.setConfigProvider(configProvider) >>> configuredLogger(queue))
      }

    }
  )
}
