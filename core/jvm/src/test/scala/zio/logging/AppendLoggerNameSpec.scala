package zio.logging

import zio.test._
import zio.logging.appendLoggerName
import zio.FiberRef

object AppendLoggerNameSpec extends ZIOSpecDefault {

  val spec: Spec[Environment, Any] = suite("AppendLoggerNameSpec")(
    test("appendLoggerName") {
      for {
        name <- FiberRef.currentLogAnnotations.get.map(annotations =>
                  annotations.get(loggerNameAnnotationKey)
                ) @@ appendLoggerName("logging") @@ appendLoggerName("zio")

      } yield assertTrue(name == Some("zio.logging"))
    }
  )
}
