package zio.logging.internal

import zio.logging.ConsoleLoggerConfig
import zio.test._
import zio.logging._
import zio.{ Chunk, Config, ConfigProvider, LogLevel, Queue, Runtime, Schedule, ZIO, ZLayer, _ }

object ReconfigurableLoggerSpec extends ZIOSpecDefault {

  def configuredLogger(
    queue: zio.Queue[String],
    reconfigurations: zio.Ref[Int],
    configPath: String = "logger"
  ): ZLayer[Any, Config.Error, Unit] =
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
                   .flatMap { r =>
                     reconfigurations.update(_ + 1).when(r)
                   }
                   .scheduleFork(Schedule.fixed(200.millis))
        _     <- ZIO.withLoggerScoped(logger)
      } yield ()
    }

  val spec: Spec[Environment, Any] = suite("ReconfigurableLogger")(
    test("log with changed config") {

      val initialProperties = Map(
        "logger/format"                                              -> "%message",
        "logger/filter/rootLevel"                                    -> LogLevel.Info.label,
        "logger/filter/mappings/zio.logging.example.LivePingService" -> LogLevel.Debug.label
      )

      for {
        _ <- ZIO.foreach(initialProperties) { case (k, v) =>
               TestSystem.putProperty(k, v).as(k -> v)
             }

        queue <- Queue.unbounded[String]

        reconfigurationsCounter <- Ref.make(0)

        runTest =
          for {
            _            <- ZIO.logInfo("info")
            _            <- ZIO.logDebug("debug")
            elements1    <- queue.takeAll
            _            <- TestSystem.putProperty("logger/format", "%level %message")
            _            <- ZIO.sleep(500.millis)
            _            <- ZIO.logWarning("warn")
            elements2    <- queue.takeAll
            _            <- TestSystem.putProperty("logger/format", "L: %level M: %message")
            _            <- TestSystem.putProperty("logger/filter/rootLevel", LogLevel.Debug.label)
            _            <- ZIO.sleep(500.millis)
            _            <- ZIO.logDebug("debug")
            elements3    <- queue.takeAll
            reconfigured <- reconfigurationsCounter.get
          } yield assertTrue(
            elements1 == Chunk("info") && elements2 == Chunk("WARN warn") && elements3 == Chunk(
              "L: DEBUG M: debug"
            ) && reconfigured == 2
          )

        result <-
          runTest.provide(
            Runtime.removeDefaultLoggers >>> Runtime
              .setConfigProvider(ConfigProvider.fromProps("/")) >>> configuredLogger(queue, reconfigurationsCounter)
          )
      } yield result

    }
  ) @@ TestAspect.withLiveClock
}
