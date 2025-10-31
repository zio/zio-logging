package zio.logging

import zio.test.Assertion._
import zio.test._
import zio.{ Chunk, Runtime, ZIO, _ }

import java.util.UUID

object LogAnnotationSpec extends ZIOSpecDefault {

  private def getOutputLogAnnotationValues(
    annotation: zio.logging.LogAnnotation[_]
  ): ZIO[Any, Nothing, Chunk[Option[String]]] =
    ZTestLogger.logOutput.map { loggerOutput =>
      loggerOutput.map(_.context.get(logContext).flatMap(_.get(annotation.name)))
    }

  override def spec: Spec[TestEnvironment, Any] = suite("LogAnnotationSpec")(
    test("annotations aspect combinators") {
      assertTrue(
        (zio.logging.LogAnnotation.UserId("u") @@ zio.logging.LogAnnotation.TraceId(UUID.randomUUID()))
          .isInstanceOf[zio.logging.LogAnnotation.LogAnnotationAspect]
      ) && assertTrue(
        (zio.logging.LogAnnotation.UserId("u") >>> zio.logging.LogAnnotation.TraceId(UUID.randomUUID()))
          .isInstanceOf[zio.logging.LogAnnotation.LogAnnotationAspect]
      )
    },
    test("annotations from multiple levels with @@") {
      val users = Chunk.fill(2)(UUID.randomUUID())
      for {
        traceId        <- ZIO.succeed(UUID.randomUUID())
        _              <- ZIO.foreach(users) { uId =>
                            {
                              ZIO.logInfo("start") *> ZIO.sleep(100.millis) *> ZIO.logInfo("stop")
                            } @@ zio.logging.LogAnnotation.UserId(uId.toString) *> ZIO.logInfo("next")
                          } @@ zio.logging.LogAnnotation.TraceId(traceId)
        outputTraceIds <- getOutputLogAnnotationValues(zio.logging.LogAnnotation.TraceId)
        outputUserIds  <- getOutputLogAnnotationValues(zio.logging.LogAnnotation.UserId)
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
                            } @@ (zio.logging.LogAnnotation.UserId(uId.toString) @@ zio.logging.LogAnnotation.TraceId(traceId))
                          }
        outputTraceIds <- getOutputLogAnnotationValues(zio.logging.LogAnnotation.TraceId)
        outputUserIds  <- getOutputLogAnnotationValues(zio.logging.LogAnnotation.UserId)
      } yield assert(outputTraceIds.flatten)(equalTo(Chunk.fill(4)(traceId.toString))) &&
        assert(outputUserIds.flatten)(equalTo(users.flatMap(u => Chunk.fill(2)(u.toString))))
    }.provideLayer(ZTestLogger.default)
  ).provideLayer(Runtime.removeDefaultLoggers) @@ TestAspect.withLiveClock
}
