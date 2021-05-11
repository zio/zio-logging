package zio.logging.slf4j

import zio.logging._
import zio.test.Assertion._
import zio.test.TestAspect.{ exceptDotty, sequential }
import zio.test.{ DefaultRunnableSpec, _ }
import zio.{ UIO, ULayer }

import java.util.UUID
import scala.jdk.CollectionConverters._
import zio.test.environment.TestEnvironment

object Slf4jLoggerTest extends DefaultRunnableSpec {

  val uuid1: UUID                    = UUID.randomUUID()
  val logLayerOptIn: ULayer[Logging] =
    Slf4jLogger.makeWithAnnotationsAsMdc(
      mdcAnnotations = List(LogAnnotation.CorrelationId, LogAnnotation.Level)
    ) >>> Logging.withContext(LogContext.empty.annotate(LogAnnotation.CorrelationId, Some(uuid1)))

  val logLayerOptOut: ULayer[Logging] =
    Slf4jLogger.makeWithAllAnnotationsAsMdc(Set(LogAnnotation.Level.name)) >>>
      Logging.withContext(LogContext.empty.annotate(LogAnnotation.CorrelationId, Some(uuid1)))

  def spec: ZSpec[TestEnvironment, Any] =
    suite("slf4j logger")(
      testM("logger name from stack trace") {
        for {
          uuid2 <- UIO(UUID.randomUUID())
          _      = TestAppender.reset()
          _     <- log.info("log stmt 1") *>
                     log.locally(_.annotate(LogAnnotation.CorrelationId, Some(uuid2))) {
                       log.info("log stmt 1_1") *>
                         log.info("log stmt 1_2")
                     } *>
                     log.info("log stmt 2")
        } yield {
          val testEvs = TestAppender.events
          assert(testEvs.map(_.getLoggerName).map(_.substring(0, 34)).distinct)(
            equalTo(List("zio.logging.slf4j.Slf4jLoggerTest$"))
          )
        }
      }.provideCustomLayer(logLayerOptIn) @@ exceptDotty,
      testM("test with opt in annotations") {
        for {
          uuid2 <- UIO(UUID.randomUUID())
          _      = TestAppender.reset()
          _     <- log.info("log stmt 1") *>
                     log.locally(_.annotate(LogAnnotation.CorrelationId, Some(uuid2))) {
                       log.info("log stmt 1_1") *>
                         log.info("log stmt 1_2")
                     } *>
                     log.info("log stmt 2")
        } yield {
          val testEvs = TestAppender.events
          assert(testEvs.size)(equalTo(4)) &&
          assert(testEvs.map(_.getMessage))(
            equalTo(List("log stmt 1", "log stmt 1_1", "log stmt 1_2", "log stmt 2"))
          ) &&
          assert(testEvs.map(_.getMDCPropertyMap.asScala("correlation-id")))(
            equalTo(List(uuid1.toString, uuid2.toString, uuid2.toString, uuid1.toString))
          )
        }
      }.provideCustomLayer(logLayerOptIn),
      testM("test with opt-out annotations") {
        for {
          uuid2 <- UIO(UUID.randomUUID())
          _      = TestAppender.reset()
          _     <- log.info("log stmt 1") *>
                     log.locally(_.annotate(LogAnnotation.CorrelationId, Some(uuid2))) {
                       log.info("log stmt 1_1") *>
                         log.info("log stmt 1_2")
                     } *>
                     log.info("log stmt 2")
        } yield {
          val testEvs = TestAppender.events
          assert(testEvs.size)(equalTo(4)) &&
          assert(testEvs.map(_.getMessage))(
            equalTo(List("log stmt 1", "log stmt 1_1", "log stmt 1_2", "log stmt 2"))
          ) &&
          assert(testEvs.map(_.getMDCPropertyMap.asScala("correlation-id")))(
            equalTo(List(uuid1.toString, uuid2.toString, uuid2.toString, uuid1.toString))
          ) &&
          assert(testEvs.map(_.getMDCPropertyMap.asScala.get(LogAnnotation.Level.name)))(
            equalTo(List(None, None, None, None))
          )
        }
      }.provideCustomLayer(logLayerOptOut)
    ) @@ sequential
}
