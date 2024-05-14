package zio.logging

import zio.logging.{ LogAnnotation, logContext }
import zio.test.Assertion._
import zio.test._
import zio.{ Chunk, Runtime, ZIO, _ }

import java.util.UUID

object LogAnnotationSpec extends ZIOSpecDefault {

  private def getOutputLogAnnotationValues(annotation: LogAnnotation[_]): ZIO[Any, Nothing, Chunk[Option[String]]] =
    ZTestLogger.logOutput.map { loggerOutput =>
      loggerOutput.map(_.context.get(logContext).flatMap(_.get(annotation.name)))
    }

  override def spec: Spec[TestEnvironment, Any] = suite("LogAnnotationSpec")(
    test("annotations aspect combinators") {
      assertTrue(
        (LogAnnotation.UserId("u") @@ LogAnnotation.TraceId(UUID.randomUUID()))
          .isInstanceOf[LogAnnotation.LogAnnotationAspect]
      ) && assertTrue(
        (LogAnnotation.UserId("u") >>> LogAnnotation.TraceId(UUID.randomUUID()))
          .isInstanceOf[LogAnnotation.LogAnnotationAspect]
      )
    },
    test("annotations from multiple levels with @@") {
      val users = Chunk.fill(2)(UUID.randomUUID())
      for {
        traceId        <- ZIO.succeed(UUID.randomUUID())
        _              <- ZIO.foreach(users) { uId =>
                            {
                              ZIO.logInfo("start") *> ZIO.sleep(100.millis) *> ZIO.logInfo("stop")
                            } @@ LogAnnotation.UserId(uId.toString) *> ZIO.logInfo("next")
                          } @@ LogAnnotation.TraceId(traceId)
        outputTraceIds <- getOutputLogAnnotationValues(LogAnnotation.TraceId)
        outputUserIds  <- getOutputLogAnnotationValues(LogAnnotation.UserId)
      } yield assert(outputTraceIds.flatten)(equalTo(Chunk.fill(6)(traceId.toString))) &&
        assert(outputUserIds.flatten)(equalTo(users.flatMap(u => Chunk.fill(2)(u.toString))))
    }.provideLayer(ZTestLogger.default),
    test("annotations from same levels with @@") {
      val users = Chunk.fill(2)(UUID.randomUUID())
      for {
        traceId        <- ZIO.succeed(UUID.randomUUID())
        _              <- ZIO.foreach(users) { uId =>
                            {
                              ZIO.logInfo("start") *> ZIO.sleep(100.millis) *> ZIO.logInfo("stop")
                            } @@ (LogAnnotation.UserId(uId.toString) @@ LogAnnotation.TraceId(traceId))
                          }
        outputTraceIds <- getOutputLogAnnotationValues(LogAnnotation.TraceId)
        outputUserIds  <- getOutputLogAnnotationValues(LogAnnotation.UserId)
      } yield assert(outputTraceIds.flatten)(equalTo(Chunk.fill(4)(traceId.toString))) &&
        assert(outputUserIds.flatten)(equalTo(users.flatMap(u => Chunk.fill(2)(u.toString))))
    }.provideLayer(ZTestLogger.default)
  ).provideLayer(Runtime.removeDefaultLoggers) @@ TestAspect.withLiveClock
}
