package zio.logging.backend

import org.slf4j.MarkerFactory
import zio.logging.backend.TestAppender.LogEntry
import zio.logging.{ LogAnnotation, LogFormat }
import zio.test.Assertion._
import zio.test._
import zio.{ LogLevel, Runtime, ZIO, ZIOAspect, _ }

import java.util.UUID
import scala.annotation.tailrec

object SLF4JSpec extends ZIOSpecDefault {

  val loggerDefault: ZLayer[Any, Nothing, Unit] =
    Runtime.removeDefaultLoggers >>> SLF4J.slf4j

  val loggerTraceAnnotation: ZLayer[Any, Nothing, Unit] =
    Runtime.removeDefaultLoggers >>> SLF4J.slf4j(
      LogFormat.logAnnotation(LogAnnotation.TraceId) + LogFormat.line + LogFormat.cause
    )

  val loggerUserAnnotation: ZLayer[Any, Nothing, Unit] =
    Runtime.removeDefaultLoggers >>> SLF4J.slf4j(
      LogFormat.annotation("user") + LogFormat.line + LogFormat.cause
    )

  val loggerLineCause: ZLayer[Any, Nothing, Unit] =
    Runtime.removeDefaultLoggers >>> SLF4J.slf4j(LogFormat.line + LogFormat.cause)

  val loggerLine: ZLayer[Any, Nothing, Unit] =
    Runtime.removeDefaultLoggers >>> SLF4J.slf4j(LogFormat.line)

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

  def startStopAssert(loggerOutput: Chunk[LogEntry], loggerName: String = "zio.logging.backend.SLF4JSpec"): TestResult =
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

  def someErrorAssert(loggerOutput: Chunk[LogEntry], loggerName: String = "zio.logging.backend.SLF4JSpec"): TestResult =
    assertTrue(loggerOutput.size == 1) && assert(loggerOutput(0).loggerName)(
      equalTo(loggerName)
    ) && assert(loggerOutput(0).logLevel)(
      equalTo(LogLevel.Error)
    ) && assert(loggerOutput(0).message)(
      equalTo("Calculation error")
    )

  val spec: Spec[Environment, Any] = suite("SLF4JSpec")(
    test("log only user annotation into MDC") {
      startStop().map { case (_, users) =>
        val loggerOutput = TestAppender.logOutput
        startStopAssert(loggerOutput) && assert(loggerOutput.map(_.mdc.get(LogAnnotation.TraceId.name)))(
          equalTo(Chunk.fill(5)(None))
        ) && assert(loggerOutput.map(_.mdc.get("user")))(
          equalTo(users.flatMap(u => Chunk.fill(2)(Some(u.toString))) :+ None)
        )
      }
    }.provide(loggerUserAnnotation),
    test("log only trace annotation into MDC") {
      startStop().map { case (traceId, _) =>
        val loggerOutput = TestAppender.logOutput
        startStopAssert(loggerOutput) && assert(loggerOutput.map(_.mdc.get(LogAnnotation.TraceId.name)))(
          equalTo(
            Chunk.fill(4)(Some(traceId.toString)) :+ None
          )
        ) && assert(loggerOutput.map(_.mdc.get("user")))(
          equalTo(Chunk.fill(5)(None))
        )
      }
    }.provide(loggerTraceAnnotation),
    test("log all annotations into MDC with custom logger name") {
      (startStop() @@ SLF4J.loggerName("my-logger")).map { case (traceId, users) =>
        val loggerOutput = TestAppender.logOutput
        startStopAssert(loggerOutput, "my-logger") && assert(loggerOutput.map(_.mdc.get(LogAnnotation.TraceId.name)))(
          equalTo(
            Chunk.fill(4)(Some(traceId.toString)) :+ None
          )
        ) && assert(loggerOutput.map(_.mdc.get("user")))(
          equalTo(users.flatMap(u => Chunk.fill(2)(Some(u.toString))) :+ None)
        ) && assert(loggerOutput.map(_.mdc.contains(SLF4J.loggerNameAnnotationName)))(
          equalTo(Chunk.fill(5)(false))
        )
      }
    }.provide(loggerDefault),
    test("logger name changes") {
      val users = Chunk.fill(2)(UUID.randomUUID())
      for {
        traceId <- ZIO.succeed(UUID.randomUUID())
        _        = TestAppender.reset()
        _       <- ZIO.logInfo("Start") @@ SLF4J.loggerName("root-logger")
        _       <- ZIO.foreach(users) { uId =>
                     {
                       ZIO.logInfo("Starting user operation") *> ZIO.sleep(500.millis) *> ZIO.logInfo(
                         "Stopping user operation"
                       )
                     } @@ ZIOAspect.annotated("user", uId.toString) @@ SLF4J.loggerName("user-logger")
                   } @@ LogAnnotation.TraceId(traceId) @@ SLF4J.loggerName("user-root-logger")
        _       <- ZIO.logInfo("Done") @@ SLF4J.loggerName("root-logger")
      } yield {
        val loggerOutput = TestAppender.logOutput
        assertTrue(loggerOutput.forall(_.logLevel == LogLevel.Info)) && assert(loggerOutput.map(_.message))(
          equalTo(
            Chunk(
              "Start",
              "Starting user operation",
              "Stopping user operation",
              "Starting user operation",
              "Stopping user operation",
              "Done"
            )
          )
        ) && assert(loggerOutput.map(_.loggerName))(
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
      (someError() @@ SLF4J.loggerName("my-logger")).map { _ =>
        val loggerOutput = TestAppender.logOutput
        someErrorAssert(loggerOutput, "my-logger") && assertTrue(
          loggerOutput(0).cause.exists(_.contains("input < 1"))
        ) &&
        assertTrue(!loggerOutput(0).mdc.contains(SLF4J.loggerNameAnnotationName))
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
    }.provide(loggerDefault),
    test("not log messages denied by marker") {
      val confidentialMarker = SLF4J.LogMarker(MarkerFactory.getMarker("CONFIDENTIAL"))
      for {
        _ <- ZIO.succeed(TestAppender.reset())
        _ <- ZIO.logInfo("not confidential info")
        _ <- ZIO.logInfo("confidential info") @@ confidentialMarker
        _ <- ZIO.logWarning("not confidential warn")
        _ <- ZIO.logWarning("confidential warn") @@ confidentialMarker
        _ <- ZIO.logError("not confidential error")
        _ <- ZIO.logError("confidential error") @@ confidentialMarker
        _ <- ZIO.logFatal("not confidential fatal")
      } yield {
        val loggerOutput = TestAppender.logOutput
        assert(loggerOutput.map(_.message))(
          equalTo(
            Chunk(
              "not confidential info",
              "not confidential warn",
              "not confidential error",
              "not confidential fatal"
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
