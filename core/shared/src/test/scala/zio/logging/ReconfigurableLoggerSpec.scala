package zio.logging

import zio.test._
import zio.{ Chunk, Config, ConfigProvider, LogLevel, Queue, Runtime, Schedule, ZIO, ZLayer, _ }

object ReconfigurableLoggerSpec extends ZIOSpecDefault {

  def configuredLogger(
    queue: zio.Queue[String]
  ): ZLayer[Any, Config.Error, Unit] =
    ZLayer.scoped {
      for {
        logger <- ReconfigurableLogger
                    .make[Any, Config.Error, String, Any, ConsoleLoggerConfig](
                      ConsoleLoggerConfig.load(),
                      (config, _) =>
                        ZIO.succeed {
                          config.format.toLogger.map { line =>
                            zio.Unsafe.unsafe { implicit u =>
                              Runtime.default.unsafe.run(queue.offer(line))
                            }
                          }.filter(config.toFilter)
                        },
                      Schedule.fixed(200.millis)
                    )
        _      <- ZIO.withLoggerScoped(logger)
      } yield ()
    }

  val spec: Spec[Environment, Any] = suite("ReconfigurableLogger2")(
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

        runTest =
          for {
            _         <- ZIO.logInfo("info")
            _         <- ZIO.logDebug("debug")
            elements1 <- queue.takeAll
            _         <- TestSystem.putProperty("logger/format", "%level %message")
            _         <- ZIO.sleep(1000.millis)
            _         <- ZIO.logWarning("warn")
            _         <- ZIO.logDebug("debug")
            elements2 <- queue.takeAll
            _         <- TestSystem.putProperty("logger/format", "L: %level M: %message")
            _         <- TestSystem.putProperty("logger/filter/rootLevel", LogLevel.Debug.label)
            _         <- ZIO.sleep(1000.millis)
            _         <- ZIO.logDebug("debug")
            elements3 <- queue.takeAll
          } yield assertTrue(
            elements1 == Chunk("info") && elements2 == Chunk("WARN warn") && elements3 == Chunk(
              "L: DEBUG M: debug"
            )
          )

        result <-
          runTest.provide(
            Runtime.removeDefaultLoggers >>> Runtime
              .setConfigProvider(ConfigProvider.fromProps("/")) >>> configuredLogger(queue)
          )
      } yield result

    }
  ) @@ TestAspect.withLiveClock
}
