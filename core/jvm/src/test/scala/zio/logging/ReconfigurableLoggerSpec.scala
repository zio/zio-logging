package zio.logging

import zio.test._
import zio.{
  Cause,
  Chunk,
  Config,
  ConfigProvider,
  FiberId,
  FiberRefs,
  LogLevel,
  LogSpan,
  Queue,
  Runtime,
  Schedule,
  Trace,
  UIO,
  ZIO,
  ZLayer,
  ZLogger
}
import zio._
import java.util.concurrent.atomic.AtomicReference

object ReconfigurableLoggerSpec extends ZIOSpecDefault {

  sealed trait ReconfigurableLogger[-Message, +Output, Config] extends ZLogger[Message, Output] {

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
        _     <- ZIO
                   .config(ConsoleLoggerConfig.config.nested(configPath))
                   .map { newConfig =>
                     logger.reconfigureIfChanged(newConfig)
                   }
                   .scheduleFork(Schedule.fixed(200.millis))
        _     <- ZIO.withLoggerScoped(logger)
      } yield ()
    }

  val spec: Spec[Environment, Any] = suite("ReconfigurableLogger")(
    test("log") {

      val initialProperties = Map(
        "logger/format"                                              -> "%message",
        "logger/filter/rootLevel"                                    -> LogLevel.Info.label,
        "logger/filter/mappings/zio.logging.example.LivePingService" -> LogLevel.Debug.label
      )

      ZIO
        .foreach(initialProperties) { case (k, v) =>
          TestSystem.putProperty(k, v).as(k -> v)
        }
        .flatMap { _ =>
          Queue.unbounded[String].flatMap { queue =>
            val runTest =
              for {
                _         <- ZIO.logInfo("info")
                _         <- ZIO.logDebug("debug")
                elements1 <- queue.takeAll
                _         <- TestSystem.putProperty("logger/format", "%level %message")
                _         <- ZIO.sleep(500.millis)
                _         <- ZIO.logWarning("warn")
                elements2 <- queue.takeAll
                _         <- TestSystem.putProperty("logger/format", "L: %level M: %message")
                _         <- TestSystem.putProperty("logger/filter/rootLevel", LogLevel.Debug.label)
                _         <- ZIO.sleep(500.millis)
                _         <- ZIO.logDebug("debug")
                elements3 <- queue.takeAll
              } yield assertTrue(
                elements1 == Chunk("info") && elements2 == Chunk("WARN warn") && elements3 == Chunk("L: DEBUG M: debug")
              )

            runTest.provide(
              Runtime.removeDefaultLoggers >>> Runtime
                .setConfigProvider(ConfigProvider.fromProps("/")) >>> configuredLogger(queue)
            )
          }
        }

    } @@ TestAspect.withLiveClock
  )
}
