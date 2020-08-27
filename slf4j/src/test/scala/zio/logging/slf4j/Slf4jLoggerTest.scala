package zio.logging.slf4j

import java.util.UUID

import zio.{ UIO, ULayer }
import zio.test.DefaultRunnableSpec
import zio.logging._
import zio.test._
import zio.test.Assertion._

import scala.jdk.CollectionConverters._

object Slf4jLoggerTest extends DefaultRunnableSpec {

  val uuid1                     = UUID.randomUUID()
  val logLayer: ULayer[Logging] =
    Slf4jLogger.makeWithAnnotationsAsMdc(
      mdcAnnotations = List(LogAnnotation.CorrelationId, LogAnnotation.Level),
      initialContext = LogContext.empty.annotate(LogAnnotation.CorrelationId, Some(uuid1))
    )

  def spec =
    suite("slf4j logger")(
      testM("test1") {
        for {
          uuid2 <- UIO(UUID.randomUUID())
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
      }
    ).provideCustomLayer(logLayer)
}
