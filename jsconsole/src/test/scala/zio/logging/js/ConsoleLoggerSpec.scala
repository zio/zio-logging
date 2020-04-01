package zio.logging.js

import zio.ZIO
import zio.logging.{ log, LogLevel }
import zio.test.Assertion.equalTo
import zio.test._

object ConsoleLoggerSpec extends DefaultRunnableSpec {

  override def spec: ZSpec[environment.TestEnvironment, Any] =
    suite("logger")(
      testM("simple log") {
        log(LogLevel.Trace)("test Trace") *>
          log(LogLevel.Debug)("test Debug") *>
          log(LogLevel.Info)("test Info") *>
          log(LogLevel.Warn)("test Warn") *>
          log(LogLevel.Error)("test Error") *>
          log(LogLevel.Fatal)("test Fatal") *>
          log(LogLevel.Off)("test Off") *>
          assertM(ZIO.succeed(5))(
            equalTo(
              5
            )
          )
      }
    ).provideLayerShared(ConsoleLogger.make((_, message) => message))

}
