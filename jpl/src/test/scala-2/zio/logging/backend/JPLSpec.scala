package zio.logging.backend

import zio.logging.backend.TestAppender.LogEntry
import zio.logging.{ LogAnnotation, LogFormat }
import zio.test.Assertion._
import zio.test._
import zio.{ LogLevel, Runtime, ZIO, ZIOAspect, _ }

import java.util.UUID
import scala.annotation.tailrec

object JPLSpec extends ZIOSpecDefault {

  private def jplTest(
    format: LogFormat = JPL.logFormatDefault,
    loggerName: Trace => String = JPL.getLoggerName()
  ): ZLayer[Any, Nothing, Unit] =
    Runtime.addLogger(JPL.jplLogger(format, loggerName, TestJPLoggerSystem.getLogger))

  val loggerDefault: ZLayer[Any, Nothing, Unit] =
    Runtime.removeDefaultLoggers >>> jplTest()

  val loggerTraceAnnotation: ZLayer[Any, Nothing, Unit] =
    Runtime.removeDefaultLoggers >>> jplTest(
      LogFormat.logAnnotation(LogAnnotation.TraceId) + LogFormat.line + LogFormat.cause
    )

  val loggerUserAnnotation: ZLayer[Any, Nothing, Unit] =
    Runtime.removeDefaultLoggers >>> jplTest(
      LogFormat.annotation("user") + LogFormat.line + LogFormat.cause
    )

  val loggerLineCause: ZLayer[Any, Nothing, Unit] =
    Runtime.removeDefaultLoggers >>> jplTest(LogFormat.line + LogFormat.cause)

  val loggerLine: ZLayer[Any, Nothing, Unit] =
    Runtime.removeDefaultLoggers >>> jplTest(LogFormat.line)

  def startStop(): ZIO[Any, Nothing, (UUID, Chunk[UUID])] = {
    val users = Chunk.fill(2)(UUID.randomUUID())
    for {
      traceId <- ZIO.succeed(UUID.randomUUID())
      _        = TestAppender.reset()
      _       <- ZIO.foreach(users) { uId =>
                   {
                     ZIO.logInfo("Starting operation") *> ZIO.sleep(500.millis) *> ZIO.logInfo("Stopping operation")
                   } @@ ZIOAspect.annotated("user", uId.toString)
                 } @@ LogAnnotation.TraceId(traceId)
      _       <- ZIO.logInfo("Done")
    } yield traceId -> users
  }

  def startStopAssert(
    loggerOutput: Chunk[LogEntry],
    loggerName: String = "zio.logging.backend.JPLSpec"
  ): TestResult =
    assertTrue(loggerOutput.size == 5) && assertTrue(
      loggerOutput.forall(_.loggerName == loggerName)
    ) && assertTrue(loggerOutput.forall(_.logLevel == LogLevel.Info)) && assert(loggerOutput.map(_.message))(
      equalTo(
        Chunk(
          "Starting operation",
          "Stopping operation",
          "Starting operation",
          "Stopping operation",
          "Done"
        )
      )
    )

  def someError(): ZIO[Any, Nothing, Unit] = {
    def someTestFunction(input: Int): Int = {
      @tailrec
      def innerFunction(input: Int): Int =
        if (input < 1) throw new Exception("input < 1")
        else innerFunction(input - 1)

      innerFunction(input)
    }

    for {
      start <- ZIO.succeed(10)
      _      = TestAppender.reset()
      _     <- ZIO
                 .attempt(someTestFunction(start))
                 .tap(result => ZIO.logInfo(s"Calculation result: $result"))
                 .catchAllCause(error => ZIO.logErrorCause("Calculation error", error) *> ZIO.succeed(-1))
    } yield ()
  }

  def someErrorAssert(
    loggerOutput: Chunk[LogEntry],
    loggerName: String = "zio.logging.backend.JPLSpec"
  ): TestResult =
    assertTrue(loggerOutput.size == 1) && assert(loggerOutput(0).loggerName)(
      equalTo(loggerName)
    ) && assert(loggerOutput(0).logLevel)(
      equalTo(LogLevel.Error)
    ) && assert(loggerOutput(0).message)(
      equalTo("Calculation error")
    )

  val spec: Spec[Environment, Any] = suite("JPLSpec")(
//    test("log only user annotation into MDC") {
//      startStop().map { case (_, users) =>
//        val loggerOutput = TestAppender.logOutput
//        startStopAssert(loggerOutput) && assert(loggerOutput.map(_.mdc.get(LogAnnotation.TraceId.name)))(
//          equalTo(Chunk.fill(5)(None))
//        ) && assert(loggerOutput.map(_.mdc.get("user")))(
//          equalTo(users.flatMap(u => Chunk.fill(2)(Some(u.toString))) :+ None)
//        )
//      }
//    }.provide(loggerUserAnnotation),
//    test("log only trace annotation into MDC") {
//      startStop().map { case (traceId, _) =>
//        val loggerOutput = TestAppender.logOutput
//        startStopAssert(loggerOutput) && assert(loggerOutput.map(_.mdc.get(LogAnnotation.TraceId.name)))(
//          equalTo(
//            Chunk.fill(4)(Some(traceId.toString)) :+ None
//          )
//        ) && assert(loggerOutput.map(_.mdc.get("user")))(
//          equalTo(Chunk.fill(5)(None))
//        )
//      }
//    }.provide(loggerTraceAnnotation),
//    test("log all annotations into MDC with custom logger name") {
//      (startStop() @@ JPL.loggerName("my-logger")).map { case (traceId, users) =>
//        val loggerOutput = TestAppender.logOutput
//        startStopAssert(loggerOutput, "my-logger") && assert(loggerOutput.map(_.mdc.get(LogAnnotation.TraceId.name)))(
//          equalTo(
//            Chunk.fill(4)(Some(traceId.toString)) :+ None
//          )
//        ) && assert(loggerOutput.map(_.mdc.get("user")))(
//          equalTo(users.flatMap(u => Chunk.fill(2)(Some(u.toString))) :+ None)
//        )
//      }
//    }.provide(loggerDefault),
    test("logger name changes") {
      val users = Chunk.fill(2)(UUID.randomUUID())
      for {
        traceId <- ZIO.succeed(UUID.randomUUID())
        _        = TestAppender.reset()
        _       <- ZIO.logInfo("Start") @@ JPL.loggerName("root-logger")
        _       <- ZIO.foreach(users) { uId =>
                     {
                       ZIO.logInfo("Starting user operation") *> ZIO.sleep(500.millis) *> ZIO.logInfo(
                         "Stopping user operation"
                       )
                     } @@ ZIOAspect.annotated("user", uId.toString) @@ JPL.loggerName("user-logger")
                   } @@ LogAnnotation.TraceId(traceId) @@ JPL.loggerName("user-root-logger")
        _       <- ZIO.logInfo("Done") @@ JPL.loggerName("root-logger")
      } yield {
        val loggerOutput = TestAppender.logOutput
        assertTrue(loggerOutput.forall(_.logLevel == LogLevel.Info)) && assert(loggerOutput.map(_.loggerName))(
          equalTo(
            Chunk(
              "root-logger",
              "user-logger",
              "user-logger",
              "user-logger",
              "user-logger",
              "root-logger"
            )
          )
        )
      }
    }.provide(loggerDefault),
    test("log error with cause") {
      someError().map { _ =>
        val loggerOutput = TestAppender.logOutput
        someErrorAssert(loggerOutput) && assertTrue(loggerOutput(0).cause.exists(_.contains("input < 1")))
      }
    }.provide(loggerLineCause),
    test("log error with cause with custom logger name") {
      (someError() @@ JPL.loggerName("my-logger")).map { _ =>
        val loggerOutput = TestAppender.logOutput
        someErrorAssert(loggerOutput, "my-logger") && assertTrue(loggerOutput(0).cause.exists(_.contains("input < 1")))
      }
    }.provide(loggerLineCause),
    test("log error without cause") {
      someError().map { _ =>
        val loggerOutput = TestAppender.logOutput
        someErrorAssert(loggerOutput) && assertTrue(loggerOutput(0).cause.isEmpty)
      }
    }.provide(loggerLine),
    test("log only enabled levels in configuration") {
      for {
        _ <- ZIO.succeed(TestAppender.reset())
        _ <- ZIO.logTrace("trace")
        _ <- ZIO.logDebug("debug")
        _ <- ZIO.logInfo("info")
        _ <- ZIO.logWarning("warn")
        _ <- ZIO.logError("error")
        _ <- ZIO.logFatal("fatal")
      } yield {
        val loggerOutput = TestAppender.logOutput
        assert(loggerOutput.map(_.message))(
          equalTo(
            Chunk(
              "info",
              "warn",
              "error",
              "fatal"
            )
          )
        ) && assert(loggerOutput.map(_.logLevel))(
          equalTo(
            Chunk(
              LogLevel.Info,
              LogLevel.Warning,
              LogLevel.Error,
              LogLevel.Error
            )
          )
        )
      }
    }.provide(loggerDefault)
  ) @@ TestAspect.sequential @@ TestAspect.withLiveClock
}
