package zio.logging.js

import zio.ZIO
import zio.logging.{LogLevel, Logging}
import zio.test.Assertion.equalTo
import zio.test._

object ConsoleLoggerSpec extends DefaultRunnableSpec {

  override def spec: ZSpec[environment.TestEnvironment, Any] =
    suite("logger")(
      testM("simple log") {
        Logging.log("test") *>
          Logging.log(LogLevel.Trace)("test Trace") *>
          Logging.log(LogLevel.Debug)("test Debug") *>
          Logging.log(LogLevel.Info)("test Info") *>
          Logging.log(LogLevel.Warn)("test Warn") *>
          Logging.log(LogLevel.Error)("test Error") *>
          Logging.log(LogLevel.Fatal)("test Fatal") *>
          Logging.log(LogLevel.Off)("test Off") *>
          assertM(ZIO.succeed(5))(
            equalTo(
              5
            )
          )
      }
    ).provideLayerShared(ConsoleLogger.make((_, message) => message))

}
