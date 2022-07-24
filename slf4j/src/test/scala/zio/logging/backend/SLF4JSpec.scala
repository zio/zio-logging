package zio.logging.backend

import zio.logging.backend.TestAppender.LogEntry
import zio.logging.{ LogAnnotation, LogFormat }
import zio.test.Assertion._
import zio.test._
import zio.{ LogLevel, Runtime, ZIO, ZIOAspect, _ }

import java.util.UUID
import scala.annotation.tailrec

object SLF4JSpec extends ZIOSpecDefault {

  val loggerDefault: ZLayer[Any, Nothing, Unit] =
    Runtime.removeDefaultLoggers >>> SLF4J.slf4j(LogLevel.Debug)

  val loggerTraceAnnotation: ZLayer[Any, Nothing, Unit] =
    Runtime.removeDefaultLoggers >>> SLF4J.slf4j(
      LogLevel.Debug,
      LogFormat.logAnnotation(LogAnnotation.TraceId) + LogFormat.line + LogFormat.cause
    )

  val loggerUserAnnotation: ZLayer[Any, Nothing, Unit] =
    Runtime.removeDefaultLoggers >>> SLF4J.slf4j(
      LogLevel.Debug,
      LogFormat.annotation("user") + LogFormat.line + LogFormat.cause
    )

  val loggerLineCause: ZLayer[Any, Nothing, Unit] =
    Runtime.removeDefaultLoggers >>> SLF4J.slf4j(
      LogLevel.Debug,
      LogFormat.line + LogFormat.cause
    )

  val loggerLine: ZLayer[Any, Nothing, Unit] =
    Runtime.removeDefaultLoggers >>> SLF4J.slf4j(
      LogLevel.Debug,
      LogFormat.line
    )

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

  def startStopAssert(loggerOutput: Chunk[LogEntry]): TestResult =
    assertTrue(loggerOutput.size == 5) && assertTrue(
      loggerOutput.forall(_.loggerName == "zio.logging.backend.SLF4JSpec")
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

  def someErrorAssert(loggerOutput: Chunk[LogEntry]): TestResult =
    assertTrue(loggerOutput.size == 1) && assert(loggerOutput(0).loggerName)(
      equalTo("zio.logging.backend.SLF4JSpec")
    ) && assert(loggerOutput(0).logLevel)(
      equalTo(LogLevel.Error)
    ) && assert(loggerOutput(0).message)(
      equalTo("Calculation error")
    )

  val spec: Spec[Environment, Any] = suite("SLF4JSpec")(
    test("log all annotations into MDC") {
      startStop().map { case (traceId, users) =>
        val loggerOutput = TestAppender.logOutput
        startStopAssert(loggerOutput) && assert(loggerOutput.map(_.mdc.get(LogAnnotation.TraceId.name)))(
          equalTo(
            Chunk.fill(4)(Some(traceId.toString)).appended(None)
          )
        ) && assert(loggerOutput.map(_.mdc.get("user")))(
          equalTo(users.flatMap(u => Chunk.fill(2)(Some(u.toString))).appended(None))
        )
      }
    }.provide(loggerDefault),
    test("log only user annotation into MDC") {
      startStop().map { case (_, users) =>
        val loggerOutput = TestAppender.logOutput
        startStopAssert(loggerOutput) && assert(loggerOutput.map(_.mdc.get(LogAnnotation.TraceId.name)))(
          equalTo(Chunk.fill(5)(None))
        ) && assert(loggerOutput.map(_.mdc.get("user")))(
          equalTo(users.flatMap(u => Chunk.fill(2)(Some(u.toString))).appended(None))
        )
      }
    }.provide(loggerUserAnnotation),
    test("log only trace annotation into MDC") {
      startStop().map { case (traceId, _) =>
        val loggerOutput = TestAppender.logOutput
        startStopAssert(loggerOutput) && assert(loggerOutput.map(_.mdc.get(LogAnnotation.TraceId.name)))(
          equalTo(
            Chunk.fill(4)(Some(traceId.toString)).appended(None)
          )
        ) && assert(loggerOutput.map(_.mdc.get("user")))(
          equalTo(Chunk.fill(5)(None))
        )
      }
    }.provide(loggerTraceAnnotation),
    test("log error with cause") {
      someError().map { _ =>
        val loggerOutput = TestAppender.logOutput
        someErrorAssert(loggerOutput) && assertTrue(loggerOutput(0).cause.exists(_.contains("input < 1")))
      }
    }.provide(loggerLineCause),
    test("log error without cause") {
      someError().map { _ =>
        val loggerOutput = TestAppender.logOutput
        someErrorAssert(loggerOutput) && assertTrue(loggerOutput(0).cause.isEmpty)
      }
    }.provide(loggerLine)
  ) @@ TestAspect.sequential @@ TestAspect.withLiveClock
}
